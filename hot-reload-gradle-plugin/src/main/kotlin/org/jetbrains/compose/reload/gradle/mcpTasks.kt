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
import org.jetbrains.compose.reload.InternalHotReloadApi
import org.jetbrains.compose.reload.core.HotReloadProperty
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget

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
    project.tasks.register(taskName, ComposeHotMcpServer::class.java) { task ->
        task.description = "Start MCP server for AI agent integration with the running Compose application"
        task.classpath = mcpClasspath
        task.mainClass.set("org.jetbrains.compose.reload.mcp.ComposeHotReloadMcp")
        task.standardInput = System.`in`
        task.pidFilePath.set(pidFileProvider)
    }
}

@UntrackedTask(because = "This task should always run")
@OptIn(InternalHotReloadApi::class)
internal open class ComposeHotMcpServer : JavaExec(), ComposeHotReloadOtherTask {
    @get:Internal
    val pidFilePath: Property<String> = project.objects.property<String>()

    override fun exec() {
        systemProperty(HotReloadProperty.PidFile.key, pidFilePath.get())
        super.exec()
    }
}
