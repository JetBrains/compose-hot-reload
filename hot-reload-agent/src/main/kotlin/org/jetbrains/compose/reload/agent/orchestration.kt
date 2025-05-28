/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.agent

import org.jetbrains.compose.reload.core.logging.Level
import org.jetbrains.compose.reload.core.logging.Logger
import org.jetbrains.compose.reload.core.logging.createLogger
import org.jetbrains.compose.reload.core.logging.formatLogHeader
import org.jetbrains.compose.reload.orchestration.OrchestrationClient
import org.jetbrains.compose.reload.orchestration.OrchestrationClientRole.Application
import org.jetbrains.compose.reload.orchestration.OrchestrationHandle
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.LogMessage.Companion.TAG_AGENT
import org.jetbrains.compose.reload.orchestration.OrchestrationService
import org.jetbrains.compose.reload.orchestration.invokeWhenReceived
import org.jetbrains.compose.reload.orchestration.startOrchestrationServer
import java.util.concurrent.Future
import kotlin.concurrent.thread
import kotlin.system.exitProcess

internal class AgentOrchestrationService : OrchestrationService {
    override fun getOrchestration(): OrchestrationHandle = orchestration
}

val orchestration: OrchestrationHandle by lazy { startOrchestration() }

fun OrchestrationMessage.send(): Future<Unit> {
    return orchestration.sendMessage(this)
}

internal fun launchOrchestration() {
    val logger = Logger()
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
    val logger = createLogger()
    val orchestration = run {
        /* Connecting to a server if we're instructed to */
        OrchestrationClient(Application)?.let { client ->
            val message = "Agent: 'Client' mode (connected to '${client.port}')"
            logger.info(message)
            client.sendMessage(
                OrchestrationMessage.LogMessage(
                    TAG_AGENT,
                    formatLogHeader("Agent", Level.Info),
                    message
                )
            )
            return@run client
        }

        /* Otherwise, we start our own orchestration server */
        logger.debug("Hot Reload Agent is starting in 'server' mode")
        startOrchestrationServer().also { server ->
            val message = "Agent: Server started on port '${server.port}'"
            logger.info(message)
            server.sendMessage(
                OrchestrationMessage.LogMessage(
                    TAG_AGENT,
                    formatLogHeader("Agent", Level.Info),
                    message
                )
            )
        }
    }

    Runtime.getRuntime().addShutdownHook(thread(start = false) {
        val message = "Hot Reload Agent is shutting down"
        logger.info(message)
        orchestration.sendMessage(
            OrchestrationMessage.LogMessage(
                TAG_AGENT,
                formatLogHeader("Agent", Level.Info),
                message
            )
        )
        orchestration.closeImmediately()
    })

    return orchestration
}
