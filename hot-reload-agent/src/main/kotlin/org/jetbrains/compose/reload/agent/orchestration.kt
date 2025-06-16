/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.agent

import org.jetbrains.compose.reload.core.Future
import org.jetbrains.compose.reload.core.awaitOrThrow
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.getBlocking
import org.jetbrains.compose.reload.core.getOrThrow
import org.jetbrains.compose.reload.core.invokeOnCompletion
import org.jetbrains.compose.reload.core.invokeOnValue
import org.jetbrains.compose.reload.core.launchTask
import org.jetbrains.compose.reload.core.withType
import org.jetbrains.compose.reload.orchestration.OrchestrationClient
import org.jetbrains.compose.reload.orchestration.OrchestrationClientRole.Application
import org.jetbrains.compose.reload.orchestration.OrchestrationHandle
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.LogMessage
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.LogMessage.Companion.TAG_AGENT
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ShutdownRequest
import org.jetbrains.compose.reload.orchestration.OrchestrationServer
import kotlin.concurrent.thread
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.seconds

private val logger = createLogger()

val orchestration: OrchestrationHandle by lazy {
    launchTask {
        val handle = startOrchestration()
        handle.port.awaitOrThrow()
        handle
    }.getBlocking(15.seconds).getOrThrow()
}


suspend fun OrchestrationMessage.send() {
    return orchestration.send(this)
}

fun OrchestrationMessage.sendBlocking() {
    return launchTask { orchestration.send(this@sendBlocking) }.getBlocking(15.seconds).getOrThrow()
}

fun OrchestrationMessage.sendAsync(): Future<Unit> {
    return launchTask { send() }
}

internal fun launchOrchestration() {
    orchestration.messages.withType<ShutdownRequest>().invokeOnValue { request ->
        /* The request provides a pidFile: We therefore only respect the request when the pidFile matches */
        if (!request.isApplicable()) {
            logger.warn("ShutdownRequest(${request.reason}) ignored ('isApplicable() == false)")
            return@invokeOnValue
        }

        logger.info("Received shutdown request '${request.reason}'")
        exitProcess(0)
    }


    orchestration.invokeOnCompletion {
        logger.info("Application Orchestration closed")
        exitProcess(0)
    }
}

private suspend fun startOrchestration(): OrchestrationHandle {
    val orchestration = run {
        /* Connecting to a server if we're instructed to */
        OrchestrationClient(Application)?.let { client ->
            client.connect().getOrThrow()

            val message = "Agent: 'Client' mode (connected to '${client.port.awaitOrThrow()}')"
            logger.info("Agent: 'Client' mode (connected to '${client.port.awaitOrThrow()}')")
            client.send(LogMessage(TAG_AGENT, message))
            return@run client
        }

        /* Otherwise, we start our own orchestration server */
        logger.debug("Hot Reload Agent is starting in 'server' mode")
        OrchestrationServer().also { server ->
            server.start()
            val message = "Agent: Server started on port '${server.port.awaitOrThrow()}'"
            logger.info(message)
            server.send(LogMessage(TAG_AGENT, message))
        }
    }

    Runtime.getRuntime().addShutdownHook(thread(start = false) {
        logger.orchestration("Hot Reload Agent is shutting down")
        orchestration.close()
    })

    return orchestration
}
