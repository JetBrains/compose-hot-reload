/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

@file:Suppress("UnstableApiUsage")

package org.jetbrains.compose.reload

import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.build.event.BuildEventsListenerRegistry
import org.gradle.kotlin.dsl.provideDelegate
import org.gradle.kotlin.dsl.support.serviceOf
import org.gradle.tooling.events.FinishEvent
import org.gradle.tooling.events.OperationCompletionListener
import org.gradle.tooling.events.task.TaskFailureResult
import org.gradle.tooling.events.task.TaskFinishEvent
import org.gradle.tooling.events.task.TaskSuccessResult
import org.gradle.util.GradleVersion
import org.jetbrains.compose.reload.core.PidFileInfo
import org.jetbrains.compose.reload.core.update
import org.jetbrains.compose.reload.gradle.Future
import org.jetbrains.compose.reload.gradle.projectFuture
import org.jetbrains.compose.reload.orchestration.OrchestrationClient
import org.jetbrains.compose.reload.orchestration.OrchestrationClientRole
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.BuildTaskResult
import org.jetbrains.compose.reload.orchestration.connectOrchestrationClient
import java.lang.AutoCloseable
import java.util.concurrent.atomic.AtomicReference

/**
 * This [statusService] will connect to running (hot) applications and sends notifications
 * like [OrchestrationMessage.BuildTaskResult] or [OrchestrationMessage.BuildStarted]] into the
 * orchestration. Tooling will then be able to infer the state (e.g., failure)
 *
 * ### Implementation Detail:
 * This uses a shared build service under the hood, which relies on the [hotReloadLifecycleTask]
 * to know about the currently running (hot) applications
 */
internal val Project.statusService: Future<Provider<StatusService>?> by projectFuture {
    if (GradleVersion.current() < GradleVersion.version("8.3")) {
        logger.warn("${GradleVersion.current()} does not support the Hot Reload 'statusService'")
        return@projectFuture null
    }

    val reloadLifecycleTask = project.hotReloadLifecycleTask.await() ?: return@projectFuture null

    val service = gradle.sharedServices.registerIfAbsent("StatusService ($buildTreePath)", StatusService::class.java) {
        it.parameters.projectPath.set(path)
        it.parameters.pidFiles.from(reloadLifecycleTask.get().pidFiles)
    }

    gradle.taskGraph.whenReady { graph ->
        if (reloadLifecycleTask.get().activeReloadTaskPaths.get().any { path -> graph.hasTask(path) }) {
            gradle.serviceOf<BuildEventsListenerRegistry>().onTaskCompletion(service)
        }
    }

    service
}

internal abstract class StatusService : BuildService<StatusService.Params>, OperationCompletionListener, AutoCloseable {

    interface Params : BuildServiceParameters {
        val projectPath: Property<String>
        val pidFiles: ConfigurableFileCollection
    }

    private val clients = AtomicReference<List<OrchestrationClient>>(emptyList())

    init {
        clients.getAndSet(emptyList()).forEach { it.closeGracefully() }

        val pidFiles = parameters.pidFiles.toList().mapNotNull { it.absoluteFile }.filter { it.exists() }.toSet()
        clients.set(pidFiles.mapNotNull { pidFile ->
            val port = PidFileInfo(pidFile.toPath()).leftOrNull()?.orchestrationPort ?: return@mapNotNull null
            runCatching { connectOrchestrationClient(OrchestrationClientRole.Compiler, port) }.getOrNull()
        }.onEach { client ->
            client.sendMessage(OrchestrationMessage.BuildStarted())

            client.invokeWhenClosed {
                clients.update { it - client }
            }
        })
    }

    override fun onFinish(event: FinishEvent) {
        if (event !is TaskFinishEvent) return
        val message = when (val result = event.result) {
            is TaskSuccessResult -> BuildTaskResult(
                taskId = event.descriptor.taskPath,
                isSuccess = true,
                startTime = event.result.startTime,
                endTime = event.result.endTime,
                failures = emptyList()
            )
            is TaskFailureResult -> BuildTaskResult(
                taskId = event.descriptor.taskPath,
                isSuccess = false,
                startTime = event.result.startTime,
                endTime = event.result.endTime,
                failures = result.failures.map { failure ->
                    BuildTaskResult.BuildTaskFailure(
                        message = failure.message,
                        description = failure.description,
                    )
                }
            )
            else -> return
        }

        clients.get().forEach { client ->
            client.sendMessage(message)
        }
    }

    override fun close() {
        val clients = clients.getAndSet(emptyList())
        clients.forEach { it.closeGracefully() }
    }
}
