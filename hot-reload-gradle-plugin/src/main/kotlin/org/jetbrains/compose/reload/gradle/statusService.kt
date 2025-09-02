/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

@file:Suppress("UnstableApiUsage")

package org.jetbrains.compose.reload.gradle

import org.gradle.api.Project
import org.gradle.api.logging.Logging
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.build.event.BuildEventsListenerRegistry
import org.gradle.kotlin.dsl.provideDelegate
import org.gradle.kotlin.dsl.support.serviceOf
import org.gradle.tooling.events.FinishEvent
import org.gradle.tooling.events.OperationCompletionListener
import org.gradle.tooling.events.task.TaskFailureResult
import org.gradle.tooling.events.task.TaskFinishEvent
import org.gradle.tooling.events.task.TaskSkippedResult
import org.gradle.tooling.events.task.TaskSuccessResult
import org.gradle.util.GradleVersion
import org.jetbrains.compose.reload.core.exception
import org.jetbrains.compose.reload.core.getBlocking
import org.jetbrains.compose.reload.core.getOrThrow
import org.jetbrains.compose.reload.core.launchTask
import org.jetbrains.compose.reload.core.leftOr
import org.jetbrains.compose.reload.core.update
import org.jetbrains.compose.reload.orchestration.OrchestrationClient
import org.jetbrains.compose.reload.orchestration.OrchestrationClientRole
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.BuildFinished
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.BuildTaskResult
import org.jetbrains.compose.reload.orchestration.connectBlocking
import org.jetbrains.compose.reload.orchestration.invokeOnClose
import org.jetbrains.compose.reload.orchestration.sendAsync
import java.lang.AutoCloseable
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.seconds

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

    val reloadTasks = tasks.withType(ComposeHotReloadTask::class.java)
    val activeReloadTasks = objects.listProperty(ComposeHotReloadTask::class.java)

    val service = gradle.sharedServices.registerIfAbsent("StatusService ($buildTreePath)", StatusService::class.java) {
        it.parameters.projectPath.set(path)
        it.parameters.ports.set(activeReloadTasks.map { tasks -> tasks.map { task -> task.agentPort} })
    }

    gradle.taskGraph.whenReady { graph ->
        val activeTaskNames = reloadTasks.names.filter { name -> graph.hasTask(project.absoluteProjectPath(name)) }
        activeReloadTasks.set(reloadTasks.named { it in activeTaskNames })

        if (activeTaskNames.isNotEmpty()) {
            gradle.serviceOf<BuildEventsListenerRegistry>().onTaskCompletion(service)
        }
    }

    service
}

internal abstract class StatusService : BuildService<StatusService.Params>, OperationCompletionListener, AutoCloseable {

    interface Params : BuildServiceParameters {
        val projectPath: Property<String>
        val ports: ListProperty<Any>
    }

    private val clients = AtomicReference<List<OrchestrationClient>>(emptyList())

    private val logger = Logging.getLogger(StatusService::class.java)

    private fun resolvePorts(any: Any?): List<Int> {
        return when(any) {
            is Int -> listOf(any)
            is Provider<*> -> resolvePorts(any.orNull)
            is Iterable<*> -> any.flatMap { resolvePorts(it) }
            else -> emptyList()
        }
    }

    private fun resolvePorts(): List<Int> {
        return parameters.ports.get().flatMap { any -> resolvePorts(any) }
    }

    init {
        clients.set(resolvePorts().mapNotNull { port ->
            OrchestrationClient(OrchestrationClientRole.Compiler, port).connectBlocking().leftOr { right ->
                logger.error("StatusService: Failed to connect to '$port' for '${parameters.projectPath.get()}'", right.exception)
                null
            }
        }.onEach { client ->
            logger.info("StatusService: Connected to '${client.port.getOrNull()}'")
            client.sendAsync(OrchestrationMessage.BuildStarted())
            client.invokeOnClose {
                clients.update { it - client }
            }
        })
    }

    override fun onFinish(event: FinishEvent) {
        if (clients.get().isEmpty()) return

        if (event !is TaskFinishEvent) return
        val message = when (val result = event.result) {
            is TaskSuccessResult -> BuildTaskResult(
                taskId = event.descriptor.taskPath,
                isSuccess = true,
                isSkipped = false,
                startTime = event.result.startTime,
                endTime = event.result.endTime,
                failures = emptyList()
            )
            is TaskSkippedResult -> BuildTaskResult(
                taskId = event.descriptor.taskPath,
                isSuccess = true,
                isSkipped = true,
                startTime = event.result.startTime,
                endTime = event.result.endTime,
                failures = emptyList()
            )
            is TaskFailureResult -> BuildTaskResult(
                taskId = event.descriptor.taskPath,
                isSuccess = false,
                isSkipped = false,
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
            client.sendAsync(message)
        }
    }

    override fun close() {
        val clients = clients.getAndSet(emptyList())
        launchTask {
            clients.forEach { client -> client.send(BuildFinished()) }
            clients.forEach { client -> client.close() }
        }.getBlocking(15.seconds).getOrThrow()
    }
}
