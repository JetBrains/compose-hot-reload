/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.agent

import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.orchestration.LoggerTag
import org.jetbrains.compose.reload.orchestration.OrchestrationClient
import org.jetbrains.compose.reload.orchestration.OrchestrationClientRole.Application
import org.jetbrains.compose.reload.orchestration.OrchestrationHandle
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import org.jetbrains.compose.reload.orchestration.invokeWhenReceived
import org.jetbrains.compose.reload.orchestration.startOrchestrationServer
import org.jetbrains.compose.reload.orchestration.withOrchestration
import java.util.concurrent.Future
import kotlin.concurrent.thread
import kotlin.system.exitProcess

private val logger = createLogger()

val orchestration by lazy { startOrchestration() }

fun OrchestrationMessage.send(): Future<Unit> {
    return orchestration.sendMessage(this)
}

internal fun launchOrchestration() {
    orchestration.invokeWhenReceived<OrchestrationMessage.ShutdownRequest> { request ->
        /* The request provides a pidFile: We therefore only respect the request when the pidFile matches */
        if (!request.isApplicable()) {
            logger.warn("ShutdownRequest(${request.reason}) ignored ('isApplicable() == false)")
            return@invokeWhenReceived
        }

        logger.info("Received shutdown request '${request.reason}'")
        exitProcess(0)
    }

    orchestration.invokeWhenClosed {
        logger.info("Application Orchestration closed")
        exitProcess(0)
    }
}

private fun startOrchestration(): OrchestrationHandle {
    val orchestration = run {
        /* Connecting to a server if we're instructed to */
        OrchestrationClient(Application)?.let { client ->
            logger.withOrchestration(LoggerTag.Agent, client)
                .info("Agent: 'Client' mode (connected to '${client.port}')")
            return@run client
        }

        /* Otherwise, we start our own orchestration server */
        logger.debug("Hot Reload Agent is starting in 'server' mode")
        startOrchestrationServer().also { server ->
            logger.withOrchestration(LoggerTag.Agent, server)
                .info("Agent: Server started on port '${server.port}'")
        }
    }

    Runtime.getRuntime().addShutdownHook(thread(start = false) {
        logger.withOrchestration(LoggerTag.Agent, orchestration)
            .info("Hot Reload Agent is shutting down")
        orchestration.closeImmediately()
    })

    return orchestration
}
