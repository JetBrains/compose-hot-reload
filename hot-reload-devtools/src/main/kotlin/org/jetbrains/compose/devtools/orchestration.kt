/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.devtools

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisallowComposableCalls
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.filterIsInstance
import org.jetbrains.compose.reload.core.Future
import org.jetbrains.compose.reload.core.HotReloadEnvironment
import org.jetbrains.compose.reload.core.HotReloadProperty
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.error
import org.jetbrains.compose.reload.core.exception
import org.jetbrains.compose.reload.core.getBlocking
import org.jetbrains.compose.reload.core.getOrThrow
import org.jetbrains.compose.reload.core.info
import org.jetbrains.compose.reload.core.launchTask
import org.jetbrains.compose.reload.core.leftOr
import org.jetbrains.compose.reload.orchestration.OrchestrationClient
import org.jetbrains.compose.reload.orchestration.OrchestrationClientRole
import org.jetbrains.compose.reload.orchestration.OrchestrationHandle
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import org.jetbrains.compose.reload.orchestration.OrchestrationServer
import org.jetbrains.compose.reload.orchestration.asFlow
import org.jetbrains.compose.reload.orchestration.connectAllOrchestrationListeners
import org.jetbrains.compose.reload.orchestration.connectBlocking
import org.jetbrains.compose.reload.orchestration.startOrchestrationServer
import java.util.ServiceLoader
import kotlin.time.Duration.Companion.seconds

private val logger = createLogger()

internal val orchestration: OrchestrationHandle = run {
    logger.info("Connecting 'orchestration'")

    /*
    Orchestration Server Precedence:
    */
    val handle = run handle@{
        /**
         * Allow the [OrchestrationExtension] to provide the handle:
         * This is useful e.g. when devtools itself is started in 'dev mode' which runs with hot reload.
         * In this case it will use the same orchestration provided by the agent.
         */
        ServiceLoader.load(OrchestrationExtension::class.java)
            .firstNotNullOfOrNull { extension -> extension.getOrchestration() }
            ?.let { return@handle it }


        /**
         * If the current environment already provides an orchestration port, then we should just connect there.
         * This is provided if some tool (e.g., the IDE or tests) are hosting the orchestration server.
         */
        HotReloadEnvironment.orchestrationPort?.let { port ->
            return@handle OrchestrationClient(OrchestrationClientRole.Tooling, port).connectBlocking().leftOr { error ->
                logger.error("Failed connecting 'orchestration'", error.exception)
                shutdown()
            }
        }

        /**
         * Default: The devtools process acts as supervisor and starts the orchestration server.
         */
        startOrchestrationServer()
    }

    logger.info("Connected 'orchestration'")

    /* Ensure we connect all deferred clients */
    if (handle is OrchestrationServer) {
        handle.connectAllOrchestrationListeners()
    }

    /* Communicate the orchestration port back to the parent process */
    handle.subtask {
        val orchestrationPort = handle.port.await()
        println(HotReloadProperty.OrchestrationPort.key + "=" + orchestrationPort)
        System.setProperty(HotReloadProperty.OrchestrationPort.key, orchestrationPort.toString())
    }

    handle.startLoggingDispatch()
    handle
}

internal suspend fun OrchestrationMessage.send() {
    return orchestration.send(this)
}

internal fun OrchestrationMessage.sendAsync(): Future<Unit> {
    return launchTask { orchestration.send(this@sendAsync) }
}

internal fun OrchestrationMessage.sendBlocking() {
    return launchTask { orchestration.send(this@sendBlocking) }.getBlocking(15.seconds).getOrThrow()
}

@Composable
internal inline fun <reified T> invokeWhenMessageReceived(noinline action: @DisallowComposableCalls (T) -> Unit) {
    LaunchedEffect(Unit) {
        orchestration.asFlow().filterIsInstance<T>().collect(action)
    }
}

/**
 * E.e. dev runs might want to provide a different orchestration handle (as we're not actually
 * running in production mode)
 */
internal interface OrchestrationExtension {
    fun getOrchestration(): OrchestrationHandle
}
