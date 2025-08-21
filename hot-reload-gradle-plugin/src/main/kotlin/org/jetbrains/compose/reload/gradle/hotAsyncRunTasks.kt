/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskCollection
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.UntrackedTask
import org.gradle.api.tasks.options.Option
import org.gradle.kotlin.dsl.withType
import org.jetbrains.compose.reload.core.HotReloadProperty
import org.jetbrains.compose.reload.core.LaunchMode
import org.jetbrains.compose.reload.core.PidFileInfo
import org.jetbrains.compose.reload.core.destroyWithDescendants
import org.jetbrains.compose.reload.core.issueNewDebugSessionJvmArguments
import org.jetbrains.compose.reload.core.leftOr
import java.util.concurrent.TimeUnit
import kotlin.io.path.createParentDirectories
import kotlin.io.path.isRegularFile
import kotlin.jvm.optionals.getOrNull

internal val Project.hotAsyncRunTasks: Future<TaskCollection<ComposeHotAsyncRun>> by projectFuture {
    val runTasks = hotRunTasks.await()
    val argFileTasks = hotArgFileTasks.await().associateBy { it.name }

    runTasks.forEach { runTask ->
        val argFileTask = argFileTasks.getValue(runTask.argFileTaskName())
        registerComposeHotAsyncRunTask(runTask, argFileTask)
    }

    tasks.withType<ComposeHotAsyncRun>()
}

private fun Project.registerComposeHotAsyncRunTask(
    runTask: TaskProvider<out AbstractComposeHotRun>,
    argFileTaskProvider: TaskProvider<ComposeHotArgFileTask>
) {
    tasks.register(runTask.name + "Async", ComposeHotAsyncRun::class.java) { task ->
        task.description = "${runTask.get().description} [Async]"

        task.argFile.set(argFileTaskProvider.flatMap { it.argFile })
        task.runTask = runTask

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

        task.mainClass.set(runTask.flatMap { it.mainClass })
    }
}

@UntrackedTask(because = "This task should always run")
internal open class ComposeHotAsyncRun : DefaultTask(), ComposeHotReloadRunTask {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.ABSOLUTE)
    internal val argFile = project.objects.fileProperty()

    @get:Internal
    @Transient
    internal var runTask: TaskProvider<out AbstractComposeHotRun>? = null

    @get:Internal
    internal val pidFile = project.objects.fileProperty()

    @get:InputFile
    @get:PathSensitive(PathSensitivity.ABSOLUTE)
    internal val javaBinary = project.objects.fileProperty()

    @get:Input
    internal val mainClass = project.objects.property(String::class.java)

    @get:Internal
    internal val className = project.objects.property(String::class.java)

    @get:Internal
    internal val funName = project.objects.property(String::class.java)

    @get:Internal
    internal val stdinFile = project.objects.fileProperty()

    @get:Internal
    internal val stdoutFile = project.objects.fileProperty()

    @get:Internal
    internal val stderrFile = project.objects.fileProperty()

    @get:Internal
    internal val intellijDebuggerDispatchPort = project.providers
        .environmentVariable(HotReloadProperty.IntelliJDebuggerDispatchPort.key)
        .orNull?.toIntOrNull()

    @get:Internal
    internal val orchestrationListenerPortSystemProperties =
        project.orchestrationListenerPortSystemProperties()

    @get:Internal
    internal val orchestrationListenerPortEnvironmentVariables =
        project.orchestrationListenerPortEnvironmentVariables()

    @Suppress("unused")
    @Option(option = "mainClass", description = "Override the main class name")
    fun mainClas(mainClass: String) {
        this.mainClass.set(mainClass)
    }

    @Suppress("unused")
    @Option(option = "className", description = "Provide the name of the class to execute")
    fun className(className: String) {
        this.className.set(className)
    }

    @Suppress("unused")
    @Option(option = "funName", description = "Provide the name of the function to execute")
    fun funName(funName: String) {
        this.funName.set(funName)
    }

    @Suppress("unused")
    @Option(option = "stdout", description = "Path to a file, directing stdout to")
    fun stdout(file: String) {
        stdoutFile.set(project.file(file))
    }

    @Option(option = "stderr", description = "Path to a file, directing stderr to")
    @Suppress("unused")
    fun stderr(file: String) {
        stderrFile.set(project.file(file))
    }

    @Option(option = "autoReload", description = "Enables automatic recompilation/reload once the source files change")
    @Suppress("unused")
    internal fun autoReload(enabled: Boolean) {
        runTask?.configure { it.isAutoReloadEnabled.set(enabled) }
    }

    @Suppress("unused")
    @Option(option = "auto", description = "Enables automatic recompilation/reload once the source files change")
    internal fun auto(enabled: Boolean) {
        runTask?.configure { it.isAutoReloadEnabled.set(enabled) }
    }

    @TaskAction
    fun runAsync() {
        /**
         * If the app is currently running, then we'll kill it before launching another instance.
         */
        if (pidFile.get().asFile.toPath().isRegularFile()) run pid@{
            val pidFileInfo = PidFileInfo(pidFile.get().asFile.toPath()).leftOr { return@pid }
            val pid = pidFileInfo.pid ?: return@pid
            val processHandle = ProcessHandle.of(pid).getOrNull() ?: return@pid
            logger.info("A previous run ($pid) still running, killing...")
            processHandle.destroyWithDescendants()
            processHandle.onExit().get(15, TimeUnit.SECONDS)
            logger.info("Previous run ($pid) killed")
        }

        pidFile.get().asFile.toPath().createParentDirectories()
        stdoutFile.get().asFile.toPath().createParentDirectories()
        stderrFile.get().asFile.toPath().createParentDirectories()

        val additionalJvmArguments = listOfNotNull(
            stdinFile.orNull?.asFile?.let { file -> "-D${HotReloadProperty.StdinFile.key}=${file.absolutePath}" },
            "-D${HotReloadProperty.StdoutFile.key}=${stdoutFile.get().asFile.absolutePath}",
            "-D${HotReloadProperty.StderrFile.key}=${stderrFile.get().asFile.absolutePath}",
            "-D${HotReloadProperty.LaunchMode.key}=${LaunchMode.Detached.name}",
            "-D${HotReloadProperty.MainClass.key}=${mainClass.get()}",
        ) + orchestrationListenerPortSystemProperties.orNull.orEmpty()
            .map { (key, value) -> "-D$key=$value" }


        val additionalArguments = listOfNotNull(
            *className.orNull?.let { className -> arrayOf("--className", className) }.orEmpty(),
            *funName.orNull?.let { funName -> arrayOf("--funName", funName) }.orEmpty()
        ).toTypedArray()

        val processBuilder = ProcessBuilder(
            javaBinary.get().asFile.absolutePath,
            *issueNewDebugSessionJvmArguments(name, intellijDebuggerDispatchPort),
            "@${argFile.asFile.get().absolutePath}",
            *additionalJvmArguments.toTypedArray(),
            mainClass.get(),
            *additionalArguments
        ).apply { environment().putAll(orchestrationListenerPortEnvironmentVariables.orNull.orEmpty()) }
            .redirectOutput(stdoutFile.get().asFile)
            .redirectError(stderrFile.get().asFile)

        if (stdinFile.isPresent) {
            processBuilder.redirectInput(stdinFile.get().asFile)
        }

        val process = processBuilder.start()
        logger.quiet("Started '${mainClass.get()}' in background (${process.pid()})")
    }
}
