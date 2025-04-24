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
import org.gradle.api.tasks.options.Option
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.gradle.work.DisableCachingByDefault
import org.jetbrains.compose.reload.core.HotReloadProperty
import org.jetbrains.compose.reload.core.LaunchMode
import org.jetbrains.compose.reload.core.PidFileInfo
import org.jetbrains.compose.reload.core.destroyWithDescendants
import org.jetbrains.compose.reload.core.issueNewDebugSessionJvmArguments
import org.jetbrains.compose.reload.core.leftOr
import org.jetbrains.compose.reload.gradle.core.composeReloadJetBrainsRuntimeBinary
import org.jetbrains.compose.reload.gradle.core.composeReloadStderrFile
import org.jetbrains.compose.reload.gradle.core.composeReloadStdinFile
import org.jetbrains.compose.reload.gradle.core.composeReloadStdoutFile
import org.jetbrains.compose.reload.gradle.files
import org.jetbrains.compose.reload.gradle.intellijDebuggerDispatchPort
import org.jetbrains.compose.reload.gradle.jetbrainsRuntimeLauncher
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.io.path.createParentDirectories
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
    val run = tasks.register(runTask.name + "Async", ComposeHotAsyncRun::class.java) { task ->
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

        task.mainClass.set(runTask.flatMap { it.mainClass })
    }

    val reload = tasks.register<ComposeReloadHotClasspathTask>(run.name + "Reload") {
        this.classpath.from(project.files { runTask.get().compilation.get().hotRuntimeFiles })
        this.agentPort.set(project.providers.fileContents(run.get().pidFile).asBytes.map {
            PidFileInfo(it).leftOrNull()?.orchestrationPort ?: -1
        })
        this.classesDirectory.set(runTask.get().compilation.get().composeHotClassesRuntimeDirectory)
    }

    run.configure {
        it.finalizedBy(reload)
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
        .map { it.toInt() }
        .orElse(project.intellijDebuggerDispatchPort)
        .orNull


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

    @TaskAction
    fun runAsync() {
        /**
         * If the app is currently running, then we'll kill it before launching another instance.
         */
        if (pidFile.get().asFile.toPath().isRegularFile()) run pid@{
            return
            /*val pidFileInfo = PidFileInfo(pidFile.get().asFile.toPath()).leftOr { return@pid }
            val pid = pidFileInfo.pid ?: return@pid
            val processHandle = ProcessHandle.of(pid).getOrNull() ?: return@pid
            logger.info("A previous run ($pid) still running, killing...")
            processHandle.destroyWithDescendants()
            processHandle.onExit().get(15, TimeUnit.SECONDS)
            logger.info("Previous run ($pid) killed")*/
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
        ).toTypedArray()

        val additionalArguments = listOfNotNull(
            *className.orNull?.let { className -> arrayOf("--className", className) }.orEmpty(),
            *funName.orNull?.let { funName -> arrayOf("--funName", funName) }.orEmpty()
        ).toTypedArray()

        val processBuilder = ProcessBuilder(
            javaBinary.get().asFile.absolutePath,
            *issueNewDebugSessionJvmArguments(intellijDebuggerDispatchPort),
            "@${argFile.asFile.get().absolutePath}",
            *additionalJvmArguments,
            mainClass.get(),
            *additionalArguments
        )

        if (stdinFile.isPresent) {
            processBuilder.redirectInput(stdinFile.get().asFile)
        }

        val process = processBuilder.start()
        logger.quiet("Started '${mainClass.get()}' in background (${process.pid()})")

        thread {
            process.inputStream.use {
                it.bufferedReader().forEachLine { line -> logger.quiet(line) }
            }
        }

        thread {
            process.errorStream.use {
                it.bufferedReader().forEachLine { line -> logger.error(line) }
            }
        }
    }
}
