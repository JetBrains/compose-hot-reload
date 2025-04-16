/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.withType
import org.gradle.work.DisableCachingByDefault
import org.jetbrains.compose.reload.core.HotReloadProperty
import org.jetbrains.compose.reload.core.destroyWithDescendants
import org.jetbrains.compose.reload.gradle.core.composeReloadJetBrainsRuntimeBinary
import org.jetbrains.compose.reload.gradle.core.composeReloadStderrFile
import org.jetbrains.compose.reload.gradle.core.composeReloadStdinFile
import org.jetbrains.compose.reload.gradle.core.composeReloadStdoutFile
import org.jetbrains.compose.reload.gradle.jetbrainsRuntimeLauncher
import java.util.Properties
import java.util.concurrent.TimeUnit
import kotlin.io.path.createParentDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.isRegularFile
import kotlin.jvm.optionals.getOrNull

internal fun Project.registerComposeHotAsyncRunTasks() {
    val runTasks = tasks.withType<AbstractComposeHotRun>()
    val argFileTasks = tasks.withType<ComposeHotArgfileTask>()

    runTasks.names.forEach { runTaskName ->
        val runTask = runTasks.named(runTaskName)
        val argFileTask = argFileTasks.named(runTask.argFileTaskName())
        registerComposeHotAsyncRunTask(runTask, argFileTask)
    }
}

private fun Project.registerComposeHotAsyncRunTask(
    runTask: TaskProvider<AbstractComposeHotRun>,
    argFileTaskProvider: TaskProvider<ComposeHotArgfileTask>
) {
    tasks.register(runTask.name + "Async", ComposeHotAsyncRun::class.java) { task ->
        task.argFile.set(argFileTaskProvider.flatMap { it.argFile })

        task.pidFile.set(project.provider {
            runTask.get().pidFile.get()
        })

        /* JetBrains Runtime */
        val composeReloadJetBrainsRuntimeBinary = project.composeReloadJetBrainsRuntimeBinary
        if (composeReloadJetBrainsRuntimeBinary != null) {
            task.javaBinary.set(composeReloadJetBrainsRuntimeBinary.toFile())
        } else {
            task.javaBinary.set(project.jetbrainsRuntimeLauncher().map { it.executablePath })
        }

        /* stdout */
        if (project.composeReloadStdoutFile != null) {
            task.stdoutFile.set(project.composeReloadStdoutFile?.toFile())
        } else {
            task.stdoutFile.set(project.provider {
                runTask.get().compilation.get().runBuildFile("${runTask.name}.stdout.txt").get()
            })
        }

        /* stderr */
        if (project.composeReloadStderrFile != null) {
            task.stderrFile.set(project.composeReloadStderrFile?.toFile())
        } else {
            task.stderrFile.set(project.provider {
                runTask.get().compilation.get().runBuildFile("${runTask.name}.stderr.txt").get()
            })
        }

        /* stdin */
        if (project.composeReloadStdinFile != null) {
            task.stdinFile.set(project.composeReloadStdinFile?.toFile())
        }

        task.mainClass.set(
            project.providers.gradleProperty("mainClass")
                .orElse(project.providers.systemProperty("mainClass"))
                .orElse(runTask.flatMap { it.mainClass })
        )
    }
}

@DisableCachingByDefault(because = "This task should always run")
internal open class ComposeHotAsyncRun : DefaultTask() {
    @get:InputFile
    internal val argFile = project.objects.fileProperty()

    @get:Internal
    internal val pidFile = project.objects.fileProperty()

    @get:InputFile
    internal val javaBinary = project.objects.fileProperty()

    @get:Input
    internal val mainClass = project.objects.property(String::class.java)

    @get:Internal
    internal val stdinFile = project.objects.fileProperty()

    @get:Internal
    internal val stdoutFile = project.objects.fileProperty()

    @get:Internal
    internal val stderrFile = project.objects.fileProperty()

    @TaskAction
    fun runAsync() {
        /**
         * If the app is currently running, then we'll kill it before launching another instance.
         */
        if (pidFile.get().asFile.toPath().isRegularFile()) run pid@{
            val properties = Properties()
            properties.load(pidFile.get().asFile.readBytes().inputStream())
            val pid = properties["pid"]?.toString()?.toLong() ?: return@pid
            val processHandle = ProcessHandle.of(pid).getOrNull() ?: return@pid
            logger.info("A previous run ($pid) still running, killing...")
            processHandle.destroyWithDescendants()
            processHandle.onExit().get(15, TimeUnit.SECONDS)
            logger.info("Previous run ($pid) killed")
        }

        pidFile.get().asFile.toPath().createParentDirectories()
        stdoutFile.get().asFile.toPath().createParentDirectories().deleteIfExists()
        stderrFile.get().asFile.toPath().createParentDirectories().deleteIfExists()

        val additionalArguments = listOfNotNull(
            stdinFile.orNull?.asFile?.let { file -> "-D${HotReloadProperty.StdinFile.key}=${file.absolutePath}" },
            "-D${HotReloadProperty.StdoutFile.key}=${stdoutFile.get().asFile.absolutePath}",
            "-D${HotReloadProperty.StderrFile.key}=${stderrFile.get().asFile.absolutePath}",
        ).toTypedArray()

        val processBuilder = ProcessBuilder(
            javaBinary.get().asFile.absolutePath,
            "@${argFile.asFile.get().absolutePath}",
            *additionalArguments,
            mainClass.get()
        )
            .redirectOutput(stdoutFile.get().asFile)
            .redirectError(stderrFile.get().asFile)

        if (stdinFile.isPresent) {
            processBuilder.redirectInput(stdinFile.get().asFile)
        }

        val process = processBuilder.start()
        logger.quiet("Started '${mainClass.get()}' in background (${process.pid()})")
    }
}
