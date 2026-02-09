/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.DisableCachingByDefault
import org.gradle.work.FileChange
import org.gradle.work.Incremental
import org.gradle.work.InputChanges
import org.jetbrains.compose.reload.InternalHotReloadApi
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ReloadClassesRequest
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ReloadClassesRequest.ChangeType
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ReloadClassesRequest.ChangeType.Added
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ReloadClassesRequest.ChangeType.Modified
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ReloadClassesRequest.ChangeType.Removed
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import kotlin.io.copyTo
import kotlin.io.extension
import kotlin.io.path.createParentDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.outputStream
import kotlin.use


internal val Project.hotSnapshotTasks: Future<Iterable<TaskProvider<ComposeHotSnapshotTask>>> by projectFuture {
    forAllJvmCompilations { compilation ->
        compilation.hotSnapshotTask.await()
    }
}

internal val KotlinCompilation<*>.hotSnapshotTask: Future<TaskProvider<ComposeHotSnapshotTask>> by future {
    val hotRuntimeFiles = this.hotRuntimeFiles
    project.tasks.register(this.hotSnapshotTaskName, ComposeHotSnapshotTask::class.java) { task ->
        task.description = "Takes a snapshot of the classpath of the '${this.name}' compilation"
        task.classpath.from(hotRuntimeFiles)
        task.classpathSnapshotFile.set(this.hotClasspathSnapshotFile)
        task.pendingRequestFile.set(this.hotReloadRequestFile)
        task.classesDirectory.set(this.hotClassesOutputDirectory)
    }
}


@DisableCachingByDefault(because = "Should always run")
@InternalHotReloadApi
abstract class ComposeHotSnapshotTask : DefaultTask(), ComposeHotReloadOtherTask {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Incremental
    val classpath: ConfigurableFileCollection = project.objects.fileCollection()

    @get:OutputFile
    val classpathSnapshotFile: RegularFileProperty = project.objects.fileProperty()

    @get:Internal
    val pendingRequestFile: RegularFileProperty = project.objects.fileProperty()

    /**
     * The directory which contains changed/added .class files.
     * This directory should be part of the classpath of a running application to support loading
     * new classes from this directory.
     */
    @get:Internal
    val classesDirectory: DirectoryProperty = project.objects.directoryProperty()


    @TaskAction
    fun execute(inputs: InputChanges) {
        val classpathSnapshotFile = classpathSnapshotFile.get().asFile.toPath()
        val pendingRequestFile = pendingRequestFile.get().asFile.toPath()

        if (!inputs.isIncremental) {
            logger.info("Non-incremental run: Taking classpath snapshot")
            classpathSnapshotFile.writeClasspathSnapshot(ClasspathSnapshot(classpath))
            pendingRequestFile.deleteIfExists()
            return
        }

        logger.info("Incremental run: Reloading classes")
        val snapshot = if (classpathSnapshotFile.exists()) classpathSnapshotFile.readClasspathSnapshot()
        else ClasspathSnapshot(classpath)

        try {
            val changedFiles = if (pendingRequestFile.exists()) {
                pendingRequestFile.readObject<ReloadClassesRequest>().changedClassFiles.toMutableMap()
            } else mutableMapOf()

            inputs.getFileChanges(classpath).forEach { change ->
                if (change.file.extension == "jar") {
                    changedFiles += resolveChangedJar(snapshot, change)
                } else {
                    changedFiles += resolveChangedFile(change)
                }
            }

            pendingRequestFile.createParentDirectories()
                .writeObject(ReloadClassesRequest(changedFiles))

            classpathSnapshotFile
                .createParentDirectories()
                .writeClasspathSnapshot(snapshot)
        } catch (t: Throwable) {
            /* We're in trouble; How shall we handle the snapshot? Let's try to take a new one? */
            logger.error("Failed to reload classes", t)
            runCatching { classpathSnapshotFile.deleteIfExists() }
            throw t
        }
    }

    private fun resolveChangedFile(change: FileChange): Pair<File, ChangeType> {
        val changeType = when (change.changeType) {
            org.gradle.work.ChangeType.ADDED -> Added
            org.gradle.work.ChangeType.MODIFIED -> Modified
            org.gradle.work.ChangeType.REMOVED -> Removed
        }

        val dynamicClasspathFile = classesDirectory.file(change.normalizedPath).get().asFile.absoluteFile
        when (changeType) {
            Added, Modified -> change.file.copyTo(dynamicClasspathFile, overwrite = true)
            Removed -> dynamicClasspathFile.delete()
        }

        logger.trace("[${change.changeType}] ${change.file}")
        return dynamicClasspathFile to changeType
    }

    private fun resolveChangedJar(classpathSnapshot: ClasspathSnapshot, change: FileChange): Map<File, ChangeType> {
        if (change.changeType == org.gradle.work.ChangeType.REMOVED) {
            val removed = classpathSnapshot.remove(change.file)
            return removed?.entries().orEmpty().map { entry ->
                val file = classesDirectory.file(entry).get().asFile
                file.toPath().deleteIfExists()
                file.absoluteFile
            }.associateWith { file -> Removed }
        }

        if (change.changeType == org.gradle.work.ChangeType.MODIFIED ||
            change.changeType == org.gradle.work.ChangeType.ADDED
        ) {
            val result = mutableMapOf<File, ChangeType>()

            val previousSnapshot = classpathSnapshot[change.file] ?: JarSnapshot()

            val newSnapshot = ZipFile(change.file).use { zip ->
                val resolvedChanges = zip.resolveChanges(previousSnapshot)
                resolvedChanges.changes.forEach { change ->
                    val targetFile = classesDirectory.file(change.entryName).get().asFile

                    fun copyTargetFile(entry: ZipEntry) {
                        zip.getInputStream(entry).use { input ->
                            targetFile.toPath().createParentDirectories().outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                    }

                    when (change) {
                        is ZipFileChange.Removed -> {
                            targetFile.toPath().deleteIfExists()
                            result[targetFile] = Removed
                        }
                        is ZipFileChange.Added -> {
                            copyTargetFile(change.entry)
                            result[targetFile] = Added
                        }
                        is ZipFileChange.Modified -> {
                            copyTargetFile(change.entry)
                            result[targetFile] = Modified
                        }
                    }
                }
                resolvedChanges.snapshot
            }

            classpathSnapshot[change.file] = newSnapshot
            return result
        }

        return emptyMap()
    }
}
