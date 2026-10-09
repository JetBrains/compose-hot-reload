/*
 * Copyright 2024-2026 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.gradle.android

import com.android.build.api.dsl.CommonExtension
import com.android.build.api.variant.AndroidComponentsExtension
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.property
import org.gradle.work.DisableCachingByDefault
import org.jetbrains.compose.reload.InternalHotReloadApi
import org.jetbrains.compose.reload.core.leftOr
import org.jetbrains.compose.reload.gradle.ComposeHotReloadOtherTask
import org.jetbrains.compose.reload.gradle.Future
import org.jetbrains.compose.reload.gradle.forAllAndroidCompilations
import org.jetbrains.compose.reload.gradle.future
import org.jetbrains.compose.reload.gradle.pidFileOrchestrationPort
import org.jetbrains.compose.reload.gradle.projectFuture
import org.jetbrains.compose.reload.gradle.readObject
import org.jetbrains.compose.reload.orchestration.OrchestrationClient
import org.jetbrains.compose.reload.orchestration.OrchestrationClientRole.Compiler
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ReloadClassesResult
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ReloadDexRequest
import org.jetbrains.compose.reload.orchestration.connectBlocking
import org.jetbrains.compose.reload.orchestration.sendAndAwaitBlocking
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinJvmAndroidCompilation
import java.io.File
import java.io.Serializable
import java.net.URI
import java.nio.file.Files
import kotlin.io.path.readLines
import java.nio.file.Path
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.zip.ZipFile
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createParentDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.time.Duration.Companion.seconds

/* How long the sender waits for the running application to acknowledge the reload */
private val resultTimeout = 30.seconds

/* The verbose name of a d8 lambda synthetic, e.g. 'a.b.C$$ExternalSyntheticLambda0'. */
private const val D8_LAMBDA_SYNTHETIC_MARKER = "\$\$ExternalSyntheticLambda"

/** The jar [classLoader] serves [className] from (e.g. AGP's r8, wherever this AGP keeps it), or null if absent. */
internal fun jarOf(classLoader: ClassLoader, className: String): Path? {
    val resource = classLoader.getResource(className.replace('.', '/') + ".class") ?: return null
    return Path.of(URI(resource.toString().removePrefix("jar:").substringBefore("!/")))
}

/**
 * The changed classes to reload, produced by [ComposeHotAndroidSnapshotTask] and consumed by
 * [ComposeHotAndroidReloadTask]. Serialized to the snapshot task's `pendingRequestFile` so a later,
 * separate Gradle invocation of the sender (`reload` task) can ship it.
 *
 * @param changedClasses names of the changed classes (e.g. `a.b.C$D`). The sender dexes their files.
 * @param addedClasses the *new* classes (not in the installed APK). The sender refuses them so far.
 */
internal class PendingAndroidReload(
    val changedClasses: LinkedHashSet<String>,
    val addedClasses: LinkedHashSet<String> = LinkedHashSet(),
) : Serializable {
    companion object {
        @Suppress("unused")
        private const val serialVersionUID: Long = 3L
    }
}

internal val Project.hotAndroidReloadTasks: Future<List<TaskProvider<ComposeHotAndroidReloadTask>?>> by projectFuture {
    forAllAndroidCompilations { compilation ->
        /* Only the hot build type's variants (paired with their run task via the pid file) */
        if (!compilation.isHotVariant()) return@forAllAndroidCompilations null
        compilation.hotAndroidReloadTask.await()
    }
}

private val KotlinJvmAndroidCompilation.hotAndroidReloadTask: Future<TaskProvider<ComposeHotAndroidReloadTask>> by future {
    val compilation = this
    val androidCommonExtension = project.extensions.findByType(CommonExtension::class.java)
    val androidComponentsExtension = project.extensions.findByType(AndroidComponentsExtension::class.java)
    val snapshotTask = compilation.hotAndroidSnapshotTask.await()

    project.tasks.register(hotAndroidReloadTaskName, ComposeHotAndroidReloadTask::class.java) { task ->
        task.description = "Hot reloads Android code for the '${compilation.name}' compilation"

        /* The sender only ships what the snapshot task detected. */
        task.pendingRequestFile.set(snapshotTask.flatMap { it.pendingRequestFile })
        task.dependsOn(snapshotTask)

        task.classpath.from(project.tasks.named(compilation.compileKotlinTaskName).map { it.outputs.files })
        /* The javac outputs: the sender must locate the Java classes' files too */
        task.classpath.from(project.tasks.named(compilation.hotAndroidJavacTaskName).map { it.outputs.files })
        task.classpath.from(compilation.hotAndroidProjectDependencies(ArtifactTypeDefinition.JAR_TYPE))

        /* AGP's own d8: the r8 jar AGP carries, i.e. the very build that dexed the installed APK */
        androidComponentsExtension?.let { androidComponents ->
            task.r8Jar.set(project.provider {
                jarOf(androidComponents.javaClass.classLoader, "com.android.tools.r8.D8")?.toString()
            })

            /*
             * AGP's resolved boot classpath: the android.jar of the compileSdk, plus optional
             * platform pieces.
             */
            task.libraryJars.from(androidComponents.sdkComponents.bootClasspath)
        }

        task.minSdk.set(project.hotAndroidMinSdk(compilation.name))
        androidCommonExtension?.compileOptions?.let { compileOptions ->
            task.coreLibraryDesugaring.set(compileOptions.isCoreLibraryDesugaringEnabled)
        }

        task.agentPort.set(
            pidFileOrchestrationPort.orElse(
                project.providers.gradleProperty("chr.orchestration.port").map(String::toInt)
            )
        )
        task.onlyIf("Running application is known") { task.agentPort.isPresent }
    }
}

/**
 * Sends the classes detected by [ComposeHotAndroidSnapshotTask] to the running app. Port-gated
 * (`onlyIf agentPort.isPresent`) so it only runs while the app is live. It performs no change
 * detection (that is the snapshot task's job): it dexes the detected classes' files with AGP's own
 * d8 ([AndroidClassDexer]) and ships the DEX.
 *
 * Not supported because dexing depends on more than the changed classes alone: core library desugaring
 * (Java 8 APIs desugaring for old Android devices). A `minSdk < 24` (program-wide interface-method
 * desugaring) cannot occur: the hot variant's minSdk is at least
 * [HotReloadProperty.HotReloadAndroidRuntimeMinSdk][org.jetbrains.compose.reload.core.HotReloadProperty.HotReloadAndroidRuntimeMinSdk].
 */
@DisableCachingByDefault(because = "Should always run")
@InternalHotReloadApi
abstract class ComposeHotAndroidReloadTask : DefaultTask(), ComposeHotReloadOtherTask {

    /** The serialized [PendingAndroidReload] produced by the snapshot task. */
    @get:Internal
    val pendingRequestFile: RegularFileProperty = project.objects.fileProperty()

    /**
     * The class files of the changed classes: the compilation's own outputs and its project dependencies'
     * jars — the same set the snapshot task watches. An input, so edited sources are recompiled first.
     */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val classpath: ConfigurableFileCollection = project.objects.fileCollection()

    /** The r8 jar AGP carries: AGP's own d8 (see [AndroidClassDexer]). */
    @get:Internal
    val r8Jar: Property<String> = project.objects.property<String>()

    /** AGP's boot classpath (the compileSdk's `android.jar`, …): d8's library model. */
    @get:Internal
    val libraryJars: ConfigurableFileCollection = project.objects.fileCollection()

    /** The variant's minSdk; d8's rewrites depend on it. */
    @get:Internal
    val minSdk: Property<Int> = project.objects.property<Int>()

    /** The module's `compileOptions.isCoreLibraryDesugaringEnabled`. */
    @get:Internal
    val coreLibraryDesugaring: Property<Boolean> = project.objects.property<Boolean>().value(false)

    @get:Internal
    val agentPort: Property<Int> = project.objects.property<Int>()

    @TaskAction
    fun reload() {
        val pendingFile = pendingRequestFile.get().asFile.toPath()
        val pending = if (pendingFile.exists()) pendingFile.readObject<PendingAndroidReload>() else null

        /* New classes cannot be redefined on the device so far: refuse them before anything else. */
        pending?.let { pending ->
            check(pending.addedClasses.isEmpty()) {
                "Adding new classes is not supported by the Android hot reload: " +
                    "${pending.addedClasses.sorted()}. 'RedefineClasses' cannot add classes — " +
                    "revert the addition, or restart the app to pick it up."
            }
        }

        if (pending == null || pending.changedClasses.isEmpty()) {
            logger.lifecycle("UP-TO-DATE: No changed classes to reload")
            return
        }

        val minSdk = minSdk.orNull
            ?: error("Compose Hot Reload (Android): cannot determine the minSdk of the variant")
        check(!coreLibraryDesugaring.get()) {
            "Compose Hot Reload (Android) does not support core library desugaring."
        }

        val payload: Map<String, ByteArray>
        val extractedClasses = Files.createTempDirectory("chr-reload-classes")
        try {
            val classFiles = locateClassFiles(pending.changedClasses, extractedClasses)
            payload = AndroidClassDexer.dex(
                r8Jar = Path.of(
                    r8Jar.orNull
                        ?: error("Compose Hot Reload (Android): cannot locate AGP's d8 (r8) on the build classpath")
                ),
                libraryJars = libraryJars.files.map { it.toPath() },
                minApi = minSdk,
                classFiles = classFiles,
            )
        } finally {
            @OptIn(ExperimentalPathApi::class)
            runCatching { extractedClasses.deleteRecursively() }
        }

        /*
         * The device can only *redefine* classes it has: every shipped name must exist in the APK
         * the session installed. The baseline snapshot collected those names.
         * Validation is skipped for tests.
         */
        validateAgainstApk(payload.keys, pendingFile.resolveSibling(APK_CLASS_NAMES_FILE))

        logger.lifecycle("Hot reload (Android): shipping ${payload.keys}")

        OrchestrationClient(Compiler, agentPort.get()).use { client ->
            client.connectBlocking().leftOr {
                logger.error("Failed to connect to the application's orchestration server")
                error("Failed to connect to the application's orchestration server")
            }

            val request = ReloadDexRequest(payload)
            logger.lifecycle("Sending ReloadDexRequest id=${request.messageId} classes=${payload.keys}")

            val result = client.sendAndAwaitBlocking(request, resultTimeout) { message ->
                (message as? ReloadClassesResult)?.takeIf { reply -> reply.reloadRequestId == request.messageId }
            }

            when {
                result == null -> logger.warn("No ReloadClassesResult within $resultTimeout")
                result.isSuccess -> {
                    logger.lifecycle("RELOAD OK")
                    /* Ship-once: clear pending only on success so a failed reload can be retried. */
                    pendingFile.deleteIfExists()
                }
                else -> logger.error("RELOAD FAILED: ${result.errorMessage}")
            }
        }
    }

    private fun validateAgainstApk(payloadClasses: Set<String>, apkClassNamesFile: Path) {
        if (!Files.isRegularFile(apkClassNamesFile)) {
            logger.info("No '$APK_CLASS_NAMES_FILE' from the baseline snapshot; skipping the payload validation")
            return
        }
        val apkClasses = apkClassNamesFile.readLines().filter { it.isNotBlank() }.toSet()
        val unexpected = payloadClasses - apkClasses
        if (unexpected.isEmpty()) return

        val newLambdasOnly = unexpected.all { name ->
            D8_LAMBDA_SYNTHETIC_MARKER in name &&
                name.substringBefore(D8_LAMBDA_SYNTHETIC_MARKER) in apkClasses
        }
        if (newLambdasOnly) {
            error(
                "Adding new classes is not supported by the Android hot reload: ${unexpected.sorted()}. " +
                    "The edit introduced a new lambda. Revert the addition, or restart the app to pick it up.",
            )
        }

        // Added classes are rejected before dexing, and new lambda call sites above, so this is a
        // name mismatch between the dexed payload and what AGP packaged. It is definitely a bug
        // or inconsistency with a new version of AGP.
        error(
            "Cannot hot reload ${unexpected.sorted()}: not present in the APK installed by the session. " +
                "Please report a bug report: https://youtrack.jetbrains.com/newIssue?project=CMP.",
        )
    }

    /**
     * The changed classes' `.class` files, in the compilation's own outputs or in its project
     * dependencies' jars; jar entries are extracted into [extractedClasses].
     */
    private fun locateClassFiles(
        classNames: Collection<String>,
        extractedClasses: Path,
    ): Map<String, Path> {
        val jars = HashMap<File, ZipFile>()
        val located = LinkedHashMap<String, Path>()
        try {
            for (className in classNames) {
                val entryName = className.replace('.', '/') + ".class"
                for (root in classpath.files) {
                    /* e.g. javac's output directory when the module has no Java sources */
                    if (!root.exists()) continue
                    if (root.isDirectory) {
                        val file = root.toPath().resolve(entryName)
                        if (Files.isRegularFile(file)) {
                            located[className] = file
                            break
                        }
                    } else {
                        val zip = jars.getOrPut(root) { ZipFile(root) }
                        val entry = zip.getEntry(entryName) ?: continue
                        val target = extractedClasses.resolve(entryName)
                        target.createParentDirectories()
                        zip.getInputStream(entry).use { input -> Files.copy(input, target, REPLACE_EXISTING) }
                        located[className] = target
                        break
                    }
                }
            }
        } finally {
            jars.values.forEach(ZipFile::close)
        }

        val missing = classNames.filterNot { it in located }
        check(missing.isEmpty()) {
            "Cannot find the class file(s) $missing among the compilation's own outputs " +
                "and its project dependencies"
        }
        return located
    }
}
