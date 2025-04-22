/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.property
import org.gradle.kotlin.dsl.withType
import org.gradle.work.DisableCachingByDefault
import org.gradle.work.FileChange
import org.gradle.work.Incremental
import org.gradle.work.InputChanges
import org.jetbrains.compose.reload.gradle.InternalHotReloadGradleApi
import org.jetbrains.compose.reload.gradle.capitalized
import org.jetbrains.compose.reload.gradle.core.composeReloadOrchestrationPort
import org.jetbrains.compose.reload.gradle.kotlinJvmOrNull
import org.jetbrains.compose.reload.gradle.kotlinMultiplatformOrNull
import org.jetbrains.compose.reload.orchestration.OrchestrationClientRole.Compiler
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ReloadClassesRequest
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ReloadClassesRequest.ChangeType
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ReloadClassesRequest.ChangeType.Added
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ReloadClassesRequest.ChangeType.Modified
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ReloadClassesRequest.ChangeType.Removed
import org.jetbrains.compose.reload.orchestration.connectOrchestrationClient
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import kotlin.io.path.createParentDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.outputStream
import kotlin.system.exitProcess


internal fun Project.setupComposeReloadHotClasspathTasks() {
    kotlinMultiplatformOrNull?.targets?.all { target ->
        target.compilations.all { compilation -> setupComposeReloadHotClasspathTask(compilation) }
    }

    kotlinJvmOrNull?.target?.compilations?.all { compilation -> setupComposeReloadHotClasspathTask(compilation) }

    tasks.withType<ComposeReloadHotClasspathTask>().configureEach { task ->
        task.outputs.upToDateWhen { true }
        task.group = "compose"
        task.agentPort.set(project.composeReloadOrchestrationPort)
    }
}

internal fun Project.setupComposeReloadHotClasspathTask(compilation: KotlinCompilation<*>): TaskProvider<ComposeReloadHotClasspathTask> {
    val name = composeReloadHotClasspathTaskName(compilation)
    if (name in tasks.names) return tasks.named(name, ComposeReloadHotClasspathTask::class.java)
    val hotRuntimeFiles = compilation.hotRuntimeFiles

    return tasks.register(name, ComposeReloadHotClasspathTask::class.java) { task ->
        assert(isHotReloadBuild) {
            "Expected ${ComposeReloadHotClasspathTask::class.simpleName} to be configured only during hot reload builds"
        }

        task.classpath.from(hotRuntimeFiles)
        task.dependsOn(hotRuntimeFiles)
        task.classesDirectory.set(compilation.composeHotClassesRuntimeDirectory)
    }
}

internal fun composeReloadHotClasspathTaskName(compilation: KotlinCompilation<*>): String {
    return buildString {
        append("reload")
        append(compilation.target.name.capitalized)
        append(compilation.name.capitalized)
        append("Classpath")
    }
}

@DisableCachingByDefault(because = "Should always run")
@InternalHotReloadGradleApi
open class ComposeReloadHotClasspathTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Incremental
    val classpath: ConfigurableFileCollection = project.objects.fileCollection()

    @get:Internal
    val agentPort: Property<Int> = project.objects.property<Int>()
        .convention(project.composeReloadOrchestrationPort)

    @get:OutputFile
    val classpathSnapshotFile: RegularFileProperty = project.objects.fileProperty()
        .value(project.layout.buildDirectory.file("run/$name/cp.snapshot.bin"))

    /**
     * The output directory which contains changed/added .class files.
     * This directory should be part of the classpath of a running application to support loading
     * new classes from this directory.
     */
    @get:Internal
    val classesDirectory: DirectoryProperty = project.objects.directoryProperty()
        .convention(project.layout.buildDirectory.dir("run/$name/classes"))

    @TaskAction
    fun execute(inputs: InputChanges) {
        val client = runCatching { connectOrchestrationClient(Compiler, agentPort.get()) }.getOrNull() ?: run {
            logger.quiet("Failed to create 'OrchestrationClient'!")
            exitProcess(-1)
        }

        val classpathSnapshotFile = classpathSnapshotFile.get().asFile

        val snapshot = if (classpathSnapshotFile.exists()) classpathSnapshotFile.readClasspathSnapshot()
        else ClasspathSnapshot(classpath)

        try {
            client.sendMessage(OrchestrationMessage.RecompilerReady())

            if (!inputs.isIncremental) {
                logger.debug("Non-Incremental compile: Rejecting")
                return
            }

            logger.quiet("Incremental run")
            val changedClassFiles = mutableMapOf<File, ChangeType>()
            inputs.getFileChanges(classpath).forEach { change ->
                if (change.file.isFile && change.file.extension == "class") {
                    changedClassFiles += resolveChangedClassFile(change)
                }

                if (change.file.isFile && change.file.extension == "jar") {
                    changedClassFiles += resolveChangedJar(snapshot, change)
                }
            }

            client.sendMessage(ReloadClassesRequest(changedClassFiles))

        } finally {
            client.closeGracefully()
            classpathSnapshotFile.toPath().createParentDirectories()
                .writeClasspathSnapshot(snapshot)
        }
    }

    private fun resolveChangedClassFile(change: FileChange): Pair<File, ChangeType> {
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
