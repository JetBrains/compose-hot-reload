/*
 * Copyright 2024-2026 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.gradle.android

import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.UntrackedTask
import org.gradle.initialization.BuildCancellationToken
import org.gradle.kotlin.dsl.property
import org.jetbrains.compose.reload.InternalHotReloadApi
import org.jetbrains.compose.reload.core.HOT_RELOAD_VERSION
import org.jetbrains.compose.reload.core.HotReloadProperty
import org.jetbrains.compose.reload.core.PidFileInfo
import org.jetbrains.compose.reload.core.deleteMyPidFileIfExists
import org.jetbrains.compose.reload.core.getBlocking
import org.jetbrains.compose.reload.core.getOrThrow
import org.jetbrains.compose.reload.core.invokeOnFinish
import org.jetbrains.compose.reload.core.writePidFile
import org.jetbrains.compose.reload.gradle.ComposeHotReloadRunTask
import org.jetbrains.compose.reload.gradle.Future
import org.jetbrains.compose.reload.gradle.forAllAndroidCompilations
import org.jetbrains.compose.reload.gradle.future
import org.jetbrains.compose.reload.gradle.pidFile
import org.jetbrains.compose.reload.gradle.runBuildDirectory
import org.jetbrains.compose.reload.gradle.projectFuture
import org.jetbrains.compose.reload.orchestration.startOrchestrationServer
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinJvmAndroidCompilation
import java.util.concurrent.CountDownLatch
import javax.inject.Inject
import kotlin.io.path.createDirectories
import kotlin.io.path.createParentDirectories
import kotlin.io.path.writeLines

/**
 * Runs an Android application on a device/emulator in hot reload mode
 * while the orchestration server runs on the **host**. The task therefore:
 *  1. starts a host orchestration server and records its port in the compilation's pid file (so the
 *     separate [ComposeHotAndroidReloadTask] can discover it via `pidFileOrchestrationPort`);
 *  2. bridges the device to that server with `adb reverse tcp:PORT tcp:PORT`;
 *  3. launches the (already installed) app, handing it the port and the delivered
 *     [HotReloadProperty]s via `am start` extras; the app's 'HotReloadInitProvider' bridges them
 *     into system properties, so 'HotReloadEnvironment' reads work on Android like anywhere else;
 *  4. blocks, keeping the server alive for the session, and tears the bridge down on stop.
 *
 * Installation itself is delegated to AGP's `install<Variant>` task (a task dependency).
 */
internal val Project.hotAndroidRunTasks: Future<List<TaskProvider<ComposeHotAndroidRunTask>?>> by projectFuture {
    forAllAndroidCompilations { compilation ->
        if (!compilation.isHotVariant()) return@forAllAndroidCompilations null
        compilation.hotAndroidRunTask.await()
    }
}

private val KotlinJvmAndroidCompilation.hotAndroidRunTask: Future<TaskProvider<ComposeHotAndroidRunTask>> by future {
    val compilation = this
    val androidComponents = project.extensions.getByType(ApplicationAndroidComponentsExtension::class.java)

    val snapshotTask = compilation.hotAndroidSnapshotTask.await()

    project.tasks.register(compilation.hotAndroidRunTaskName, ComposeHotAndroidRunTask::class.java) { task ->
        task.description = "Runs the Android app with Compose Hot Reload enabled"

        /* Let AGP build & install the APK first (e.g. 'installDebug'). */
        task.dependsOn("install" + compilation.name.replaceFirstChar { it.uppercase() })

        /* Establish the reload baseline at launch. */
        task.dependsOn(snapshotTask)

        task.adbExecutable.set(androidComponents.sdkComponents.adb)
        task.pidFile.set(project.layout.file(project.provider { compilation.pidFile.get().asFile }))

        /* The applicationId 'install<Variant>' installs */
        task.applicationId.set(project.hotAndroidApplicationId(compilation.name))

        /*
         * Device selection, and 'ANDROID_SERIAL' wins: AGP's install task installs on it, keeping the
         * installation and the launch together. The property picks the device when it is unset.
         */
        task.deviceSerial.set(
            project.providers.environmentVariable("ANDROID_SERIAL")
                .orElse(project.providers.gradleProperty("chr.android.serial"))
        )
        task.deviceSerialFromProperty.set(project.providers.gradleProperty("chr.android.serial"))

        task.projectPath.set(project.path)

        /* The 'HotReloadProperty's to deliver to the app via launch extras (see 'HotReloadInitProvider') */
        task.propertiesToDeliver.set(project.hotAndroidPropertiesToDeliver())

        /* Where this session's APK class names are written (see 'collectApkClassNames'). */
        task.apkClassNamesFile.set(
            compilation.runBuildDirectory("classpath").map { it.file(APK_CLASS_NAMES_FILE) }
        )
        task.apkSearchDir.set(project.layout.buildDirectory.dir("outputs/apk"))
    }
}

/**
 * The [HotReloadProperty]s delivered to the Android application (minus
 * [HotReloadProperty.OrchestrationPort], which the run task itself provides), sourced from the
 * Gradle property / environment variable of the property's key — when set.
 */
private fun Project.hotAndroidPropertiesToDeliver(): Provider<Map<String, String>> = provider {
    HotReloadProperty.entries
        .filter { HotReloadProperty.Platform.Android in it.deliveredTo && it != HotReloadProperty.OrchestrationPort }
        .mapNotNull { property ->
            (providers.gradleProperty(property.key).orNull ?: System.getenv(property.key))
                ?.takeIf { it.isNotBlank() }
                ?.let { property.key to it }
        }
        .toMap()
}

@UntrackedTask(because = "This task should always run")
@InternalHotReloadApi
abstract class ComposeHotAndroidRunTask : DefaultTask(), ComposeHotReloadRunTask {

    @get:Internal
    val adbExecutable: RegularFileProperty = project.objects.fileProperty()

    @get:Internal
    val applicationId: Property<String> = project.objects.property<String>()

    @get:Internal
    val pidFile: RegularFileProperty = project.objects.fileProperty()

    @get:Internal
    val deviceSerial: Property<String> = project.objects.property<String>()

    /** The 'chr.android.serial' Gradle property, before 'ANDROID_SERIAL' takes precedence over it. */
    @get:Internal
    val deviceSerialFromProperty: Property<String> = project.objects.property<String>()

    @get:Internal
    val projectPath: Property<String> = project.objects.property<String>()

    /** The [HotReloadProperty]s to deliver to the app as launch extras. */
    @get:Internal
    val propertiesToDeliver: MapProperty<String, String> = project.objects.mapProperty(String::class.java, String::class.java)

    /** Where this session writes the class names of the packaged hot APKs (the reload validates against them). */
    @get:Internal
    val apkClassNamesFile: RegularFileProperty = project.objects.fileProperty()

    /** AGP's APK output root ('build/outputs/apk') of the module this task runs. */
    @get:Internal
    val apkSearchDir: DirectoryProperty = project.objects.directoryProperty()

    @Inject
    protected abstract fun getCancellationToken(): BuildCancellationToken

    @TaskAction
    fun run() {
        val adb = adbExecutable.get().asFile.absolutePath
        val appId = applicationId.orNull ?: error("Cannot determine the applicationId of the installed app")
        /* Pin every adb call to one device: -s <serial> (explicit, or the sole attached device). */
        val serial = resolveDeviceSerial(adb)

        /* 1. Host orchestration server (startOrchestrationServer blocks until the port is bound). */
        val server = startOrchestrationServer()
        val port = server.port.getBlocking().getOrThrow()
        logger.lifecycle("Compose Hot Reload (Android): orchestration server listening on $port (device $serial)")

        /* 2. Publish the port via the pid file so the reload task can discover it. */
        val pidFilePath = pidFile.get().asFile.toPath()
        pidFilePath.parent?.createDirectories()
        // We use here a pid of Gradle task while desktop uses pid of a running application.
        // pid is used on desktop for async tasks to check if we have a running application from previous run.
        // On Android only port is used for reload task.
        // TODO: use applicatinId on Android, if we would need to identify Android application in the future.
        val pidInfo = PidFileInfo(ProcessHandle.current().pid(), port)
        pidFilePath.writePidFile(pidInfo)

        val latch = CountDownLatch(1)
        server.invokeOnFinish { latch.countDown() }
        getCancellationToken().addCallback { latch.countDown() }

        try {
            /* 3. Bridge the device's localhost:PORT to the host server. */
            adb(adb, serial, "reverse", "tcp:$port", "tcp:$port")

            /* 4. Launch the (already installed) app, handing it the port and the tunable
                  'HotReloadProperty's; the app's 'HotReloadInitProvider' bridges the extras into
                  system properties. */
            val component = resolveLaunchComponent(adb, serial, appId)
            val deliveredExtras = propertiesToDeliver.get().flatMap { (key, value) ->
                /* 'adb shell' joins its arguments with spaces: a value with whitespace would
                   corrupt the command, so it is skipped loudly instead. */
                if (' ' in value) {
                    logger.warn("Skipping '$key' launch extra: whitespace is not supported by 'adb shell'")
                    emptyList()
                } else listOf("--es", key, value)
            }
            val launchOutput = adbCapture(
                adb, "-s", serial, "shell", "am", "start",
                "-n", component,
                "--es", HotReloadProperty.OrchestrationPort.key, port.toString(),
                *deliveredExtras.toTypedArray(),
            )
            /* `am start` exits 0 even when it fails to resolve/launch, so check its output. */
            if ("Error:" in launchOutput) error("Failed to launch '$component':\n$launchOutput")
            logger.info(launchOutput.trim())

            /* The classes of the packaged APKs used for reload validation. */
            runCatching { collectApkClassNames() }
                .onFailure { logger.warn("Could not collect the APK's class names: $it") }

            val separator = "________________________________________________________________________________________________"
            val reloadTaskPath = projectPath.get().trimEnd(':') + ":reload"
            logger.quiet(
                buildString {
                    appendLine(" $separator")
                    appendLine("| ")
                    appendLine("| Compose Hot Reload ($HOT_RELOAD_VERSION)")
                    appendLine("| Running '$appId' on Android device $serial")
                    appendLine("| Logs: adb -s $serial logcat -s ComposeHotReload")
                    appendLine("| $separator")
                    appendLine()
                    appendLine("| Explicit Reload Mode:")
                    appendLine("| Use `./gradlew $reloadTaskPath` to hot reload the application")
                    appendLine("| $separator")
                    append("Press Ctrl-C to stop the session.")
                }
            )

            /* 5. Keep the server alive until the build is cancelled (Ctrl-C) or the server stops. */
            latch.await()
        } finally {
            /* The session is over: stop the app too. */
            runCatching { adb(adb, serial, "shell", "am", "force-stop", appId) }
            runCatching { adb(adb, serial, "reverse", "--remove", "tcp:$port") }
            runCatching { pidFilePath.deleteMyPidFileIfExists(pidInfo) }
            server.close()
        }
    }

    private fun collectApkClassNames() {
        val searchRoot = apkSearchDir.get().asFile
        val apks = searchRoot.walkTopDown()
            .filter { it.isFile && it.extension.equals("apk", ignoreCase = true) }
            /* Only the hot variant's APKs: the variant directory carries the hot build type name. */
            .filter { file -> file.parentFile?.name?.contains(hotAndroidBuildTypeName, ignoreCase = true) == true }
            .toList()
        if (apks.isEmpty()) {
            logger.warn(
                "No packaged '$hotAndroidBuildTypeName' APK found under '${searchRoot.absolutePath}'; " +
                    "skipping the APK class-name collection",
            )
            return
        }
        val names = AndroidApkClassNames.of(apks.map { it.toPath() })
        val target = apkClassNamesFile.get().asFile.toPath()
        target.createParentDirectories()
        target.writeLines(names.sorted())
        logger.info("Collected ${names.size} class name(s) from ${apks.size} packaged APK(s)")
    }

    /**
     * Resolves the target device serial: the explicit [deviceSerial] if set, otherwise the sole
     * attached device. Fails with an actionable message when zero or several devices are attached,
     * or when the property and the environment variable select different devices.
     */
    private fun resolveDeviceSerial(adb: String): String {
        val fromProperty = deviceSerialFromProperty.orNull?.takeIf { it.isNotBlank() }
        val requested = deviceSerial.orNull?.takeIf { it.isNotBlank() }
        if (fromProperty != null && requested != null && fromProperty != requested) {
            error(
                "'-Pchr.android.serial=$fromProperty' conflicts with ANDROID_SERIAL=$requested: " +
                    "the installation follows ANDROID_SERIAL. Set only one of them, or the same device in both."
            )
        }
        if (requested != null) return requested

        val devices = adbCapture(adb, "devices").lineSequence()
            .drop(1) // "List of devices attached"
            .mapNotNull { line ->
                val parts = line.trim().split(Regex("\\s+"))
                if (parts.size >= 2 && parts[1] == "device") parts[0] else null
            }
            .toList()

        return when (devices.size) {
            1 -> devices.single()
            0 -> error("No attached device/emulator found. Start one, then re-run.")
            else -> error(
                "Several devices attached (${devices.joinToString()}). " +
                    "Select one with -Pchr.android.serial=<serial> (or the ANDROID_SERIAL env var)."
            )
        }
    }

    /**
     * Resolves the launcher activity of [appId] to an explicit `package/activity` component.
     */
    private fun resolveLaunchComponent(adb: String, serial: String, appId: String): String {
        val output = adbCapture(
            adb, "-s", serial, "shell", "cmd", "package", "resolve-activity", "--brief",
            "-a", "android.intent.action.MAIN", "-c", "android.intent.category.LAUNCHER", appId,
        )
        return output.lineSequence().map { it.trim() }.lastOrNull { it.contains("/") }
            ?: error("Could not resolve a launcher activity for '$appId':\n$output")
    }

    private fun adb(adb: String, serial: String, vararg args: String) {
        val command = listOf(adb, "-s", serial, *args)
        logger.info("exec: ${command.joinToString(" ")}")
        val process = ProcessBuilder(command)
            .redirectOutput(ProcessBuilder.Redirect.INHERIT)
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .start()
        val exit = process.waitFor()
        if (exit != 0) error("Command failed (exit=$exit): ${command.joinToString(" ")}")
    }

    /** Runs an adb command and returns its captured stdout. */
    private fun adbCapture(adb: String, vararg args: String): String {
        val command = listOf(adb, *args)
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        val exit = process.waitFor()
        if (exit != 0) error("Command failed (exit=$exit): ${command.joinToString(" ")}\n$output")
        return output
    }
}
