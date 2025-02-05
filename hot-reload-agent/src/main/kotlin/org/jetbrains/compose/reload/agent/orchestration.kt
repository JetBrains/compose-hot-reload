/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.agent

import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.orchestration.OrchestrationClient
import org.jetbrains.compose.reload.orchestration.OrchestrationClientRole.Application
import org.jetbrains.compose.reload.orchestration.OrchestrationHandle
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.LogMessage
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.LogMessage.Companion.TAG_AGENT
import org.jetbrains.compose.reload.orchestration.startOrchestrationServer
import java.util.concurrent.Future
import kotlin.concurrent.thread

private val logger = createLogger()

val orchestration by lazy { startOrchestration() }

fun OrchestrationMessage.send(): Future<Unit> {
    return orchestration.sendMessage(this)
}

internal fun startOrchestration(): OrchestrationHandle {
    val orchestration = run {
        /* Connecting to a server if we're instructed to */
        OrchestrationClient(Application)?.let { client ->
            val message = "Agent: 'Client' mode (connected to '${client.port}')"
            logger.info("Agent: 'Client' mode (connected to '${client.port}')")
            client.sendMessage(LogMessage(TAG_AGENT, message))
            return@run client
        }

        /* Otherwise, we start our own orchestration server */
        logger.debug("Hot Reload Agent is starting in 'server' mode")
        startOrchestrationServer().also { server ->
            val message = "Agent: Server started on port '${server.port}'"
            logger.info(message)
            server.sendMessage(LogMessage(TAG_AGENT, message))
        }
    }

    Runtime.getRuntime().addShutdownHook(thread(start = false) {
        logger.orchestration("Hot Reload Agent is shutting down")
        orchestration.closeImmediately()
    })

    return orchestration
}
