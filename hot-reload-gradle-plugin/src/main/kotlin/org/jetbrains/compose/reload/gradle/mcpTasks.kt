/*
 * Copyright 2024-2026 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.gradle

import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.UntrackedTask
import org.gradle.kotlin.dsl.property
import org.jetbrains.compose.reload.DelicateHotReloadApi
import org.jetbrains.compose.reload.InternalHotReloadApi
import org.jetbrains.compose.reload.core.HotReloadProperty
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget

private const val DEV_APPLICATION_MAIN_CLASS = "org.jetbrains.compose.reload.jvm.DevApplication"

internal val Project.hotMcpServerTasks: Future<List<TaskProvider<ComposeHotMcpServer>>> by projectFuture {
    PluginStage.EagerConfiguration.await()

    forAllJvmTargets { target ->
        target.hotMcpServerTask.await()
    }.filterNotNull()
}

private val KotlinTarget.hotMcpServerTask: Future<TaskProvider<ComposeHotMcpServer>?> by future {
    val mainCompilation = compilations.findByName("main") ?: return@future null
    val pidFileProvider = mainCompilation.pidFile.map { it.asFile.absolutePath }
    val mcpClasspath = project.composeHotReloadMcpConfiguration
    val taskName = camelCase("hot", "mcp", "server", name)

    // Provide arg file and JBR launcher for the MCP server to let it launch a headless
    // 'DevApplication` in 'run_headless` tool. Thus MCP server provides only
    // the orchestration port and the target '--className'/'--funName' in addition.
    val headlessArgFileTask = project.registerHeadlessArgFileTask(mainCompilation, name)
    val javaBinaryProvider = project.jetbrainsRuntimeLauncher()
        .map { it.executablePath.asFile.absolutePath }
    val headlessArgFileProvider = headlessArgFileTask
        .flatMap { task -> task.argFile }.map { it.asFile.absolutePath }

    project.tasks.register(taskName, ComposeHotMcpServer::class.java) { task ->
        task.description = "Start MCP server for AI agent integration with the running Compose application"
        task.classpath = mcpClasspath
        task.mainClass.set("org.jetbrains.compose.reload.mcp.ComposeHotReloadMcp")
        task.standardInput = System.`in`
        task.pidFilePath.set(pidFileProvider)
        task.headlessJavaBinary.set(javaBinaryProvider)
        task.headlessArgFile.set(headlessArgFileProvider)
        task.dependsOn(headlessArgFileTask)
    }
}

/**
 * Registers a task that writes an argfile for headless 'DevApplication` launch.
 */
@OptIn(DelicateHotReloadApi::class)
private fun Project.registerHeadlessArgFileTask(
    compilation: KotlinCompilation<*>,
    targetName: String,
): TaskProvider<ComposeHotArgFileTask> {
    val hotClasspath = files { compilation.composeHotReloadRuntimeClasspath }
    val argFileOutput = layout.buildDirectory.file("run/${camelCase("hotMcpHeadless", targetName)}.argfile")

    val arguments = createComposeHotReloadArguments {
        setMainClass(provider { DEV_APPLICATION_MAIN_CLASS })
        setHotClasspath(hotClasspath)
        setIsHeadless(provider { true })
        setDevToolsEnabled(provider { false })
        isAutoRecompileEnabled(provider { false })
    }

    return tasks.register(camelCase("hotMcpHeadless", targetName, "argfile"), ComposeHotArgFileTask::class.java) { task ->
        task.description = "Creates the headless launch argfile used by the MCP server for target '$targetName'"
        task.runTaskName.set(targetName)
        task.argFile.set(argFileOutput)
        task.classpath.from(hotClasspath)
        task.jvmArguments.add(provider { arguments.asArguments().toList() })
        task.arguments.add(listOf(DEV_APPLICATION_MAIN_CLASS))
    }
}

@UntrackedTask(because = "This task should always run")
@OptIn(InternalHotReloadApi::class)
internal open class ComposeHotMcpServer : JavaExec(), ComposeHotReloadOtherTask {
    @get:Internal
    val pidFilePath: Property<String> = project.objects.property<String>()

    @get:Internal
    val headlessJavaBinary: Property<String> = project.objects.property<String>()

    @get:Internal
    val headlessArgFile: Property<String> = project.objects.property<String>()

    override fun exec() {
        systemProperty(HotReloadProperty.PidFile.key, pidFilePath.get())
        systemProperty(HotReloadProperty.HeadlessJavaBinary.key, headlessJavaBinary.get())
        systemProperty(HotReloadProperty.HeadlessArgFile.key, headlessArgFile.get())
        super.exec()
    }
}
