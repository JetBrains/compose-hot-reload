/*
 * Copyright 2024-2026 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.gradle.android

import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.ChangeType
import org.gradle.work.DisableCachingByDefault
import org.gradle.work.FileChange
import org.gradle.work.Incremental
import org.gradle.work.InputChanges
import org.jetbrains.compose.reload.InternalHotReloadApi
import org.jetbrains.compose.reload.gradle.ClasspathSnapshot
import org.jetbrains.compose.reload.gradle.ComposeHotReloadOtherTask
import org.jetbrains.compose.reload.gradle.Future
import org.jetbrains.compose.reload.gradle.JarSnapshot
import org.jetbrains.compose.reload.gradle.ZipFileChange
import org.jetbrains.compose.reload.gradle.future
import org.jetbrains.compose.reload.gradle.hotClasspathSnapshotFile
import org.jetbrains.compose.reload.gradle.hotReloadRequestFile
import org.jetbrains.compose.reload.gradle.readClasspathSnapshot
import org.jetbrains.compose.reload.gradle.readObject
import org.jetbrains.compose.reload.gradle.resolveChanges
import org.jetbrains.compose.reload.gradle.writeClasspathSnapshot
import org.jetbrains.compose.reload.gradle.writeObject
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinJvmAndroidCompilation
import java.util.zip.ZipFile
import kotlin.io.path.createParentDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists

/**
 * The change-detection (snapshot) task for a compilation, exposed as a [Future] so both the sender
 * ([ComposeHotAndroidReloadTask]) and [ComposeHotAndroidRunTask] (baseline at launch) can depend on it.
 */
internal val KotlinJvmAndroidCompilation.hotAndroidSnapshotTask: Future<TaskProvider<ComposeHotAndroidSnapshotTask>> by future {
    val compilation = this

    project.tasks.register(hotAndroidSnapshotTaskName, ComposeHotAndroidSnapshotTask::class.java) { task ->
        task.description = "Snapshots changed Android classes for the '${compilation.name}' compilation"

        /* The compilation's own classes */
        task.classpath.from(project.tasks.named(compilation.compileKotlinTaskName).map { it.outputs.files })
        /* AGP compiles the module's Java sources with javac: its outputs are classes of the variant too */
        task.classpath.from(project.tasks.named(compilation.hotAndroidJavacTaskName).map { it.outputs.files })

        /*
         * The classes of the modules the compilation depends on.
         */
        task.classpath.from(compilation.hotAndroidProjectDependencies(ArtifactTypeDefinition.JAR_TYPE))

        task.classpathSnapshotFile.set(compilation.hotClasspathSnapshotFile)

        task.pendingRequestFile.set(compilation.hotReloadRequestFile)
    }
}

/**
 * Change-detection half of the Android reload flow.
 *
 * It finds the changed classes in the compilation's own outputs and
 * in its project dependencies. It records their names in a [PendingAndroidReload].
 * [ComposeHotAndroidReloadTask] then resolves their DEX and ships it.
 *
 * A non-incremental run only *establishes the baseline* and reloads nothing. The run task
 * depends on this task, so the baseline is taken at launch and every later `reload` is incremental.
 */
@DisableCachingByDefault(because = "Should always run")
@InternalHotReloadApi
abstract class ComposeHotAndroidSnapshotTask : DefaultTask(), ComposeHotReloadOtherTask {

    /**
     * The compilation's own classes (the compile task's outputs) and the `jar`s view of its project dependencies.
     */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Incremental
    val classpath: ConfigurableFileCollection = project.objects.fileCollection()

    /**
     * The snapshot of the jars in [classpath]: it tells which classes of a changed jar changed.
     * Being a declared output, it also makes [InputChanges] incremental.
     */
    @get:OutputFile
    val classpathSnapshotFile: RegularFileProperty = project.objects.fileProperty()

    /** The serialized [PendingAndroidReload] handed to the sender. */
    @get:Internal
    val pendingRequestFile: RegularFileProperty = project.objects.fileProperty()

    @TaskAction
    fun snapshot(inputs: InputChanges) {
        val classpathSnapshotFile = classpathSnapshotFile.get().asFile.toPath()
        val pendingFile = pendingRequestFile.get().asFile.toPath()

        if (!inputs.isIncremental) {
            classpathSnapshotFile.createParentDirectories().writeClasspathSnapshot(ClasspathSnapshot(classpath))
            pendingFile.deleteIfExists()
            logger.info("Non-incremental run: established baseline snapshot, nothing to reload")
            return
        }

        val snapshot = if (classpathSnapshotFile.exists()) classpathSnapshotFile.readClasspathSnapshot()
        else ClasspathSnapshot(classpath)

        val changedClasses = if (pendingFile.exists()) LinkedHashSet(pendingFile.readObject<PendingAndroidReload>().changedClasses)
        else LinkedHashSet()

        /*
         * Removed classes and jars cannot be unloaded, but their names must not stay pending: their
         * files are gone, so the sender could never resolve them (f.i. a failed reload, then reverting
         * the edit).
         */
        inputs.getFileChanges(classpath).forEach { change ->
            if (change.file.extension == "jar") {
                if (change.changeType == ChangeType.REMOVED) {
                    snapshot[change.file]?.entries()?.mapNotNull(::classNameOf)?.forEach(changedClasses::remove)
                    snapshot.remove(change.file)
                } else {
                    changedClasses += resolveChangedJar(snapshot, change, changedClasses)
                }
            } else {
                val className = classNameOf(change.normalizedPath) ?: return@forEach
                if (change.changeType == ChangeType.REMOVED) changedClasses.remove(className)
                else changedClasses.add(className)
            }
        }

        classpathSnapshotFile.createParentDirectories().writeClasspathSnapshot(snapshot)
        pendingFile.createParentDirectories().writeObject(PendingAndroidReload(changedClasses))
        logger.info("Snapshot: ${changedClasses.size} pending class(es)")
    }

    /**
     * The added or modified classes of an added or modified jar; [snapshot] is updated with the jar's new
     * content. Entries removed from the jar are dropped from [changedClasses]: they cannot be unloaded,
     * but must not stay pending — their files are gone, so the sender could never resolve them
     * (f.i. a failed reload, then reverting the edit).
     */
    private fun resolveChangedJar(
        snapshot: ClasspathSnapshot,
        change: FileChange,
        changedClasses: MutableSet<String>,
    ): List<String> {
        val resolved = ZipFile(change.file).use { zip -> zip.resolveChanges(snapshot[change.file] ?: JarSnapshot()) }
        snapshot[change.file] = resolved.snapshot
        return resolved.changes.mapNotNull { zipChange ->
            val className = classNameOf(zipChange.entryName) ?: return@mapNotNull null
            if (zipChange is ZipFileChange.Removed) {
                changedClasses.remove(className)
                null
            } else className
        }
    }
}

/**
 * The class name of a `.class` file at [path] (relative to its classes root, `/`-separated), e.g.
 * `a/b/C$D.class` → `a.b.C$D`. `null` for other files, which are not reloaded (yet).
 */
internal fun classNameOf(path: String): String? {
    if (!path.endsWith(".class")) return null
    val name = path.removeSuffix(".class").replace('\\', '/')
    if (name.endsWith("module-info") || name.startsWith("META-INF/")) return null
    return name.replace('/', '.')
}
