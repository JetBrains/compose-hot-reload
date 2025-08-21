/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.orchestration

import org.jetbrains.compose.reload.InternalHotReloadApi
import org.jetbrains.compose.reload.core.Future
import org.jetbrains.compose.reload.core.Task
import org.jetbrains.compose.reload.core.WorkerThread
import org.jetbrains.compose.reload.core.awaitOrThrow
import org.jetbrains.compose.reload.core.complete
import org.jetbrains.compose.reload.core.completeExceptionally
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.dispatcher
import org.jetbrains.compose.reload.core.error
import org.jetbrains.compose.reload.core.exception
import org.jetbrains.compose.reload.core.getBlocking
import org.jetbrains.compose.reload.core.getOrThrow
import org.jetbrains.compose.reload.core.info
import org.jetbrains.compose.reload.core.invokeOnFinish
import org.jetbrains.compose.reload.core.invokeOnStop
import org.jetbrains.compose.reload.core.isFailure
import org.jetbrains.compose.reload.core.launchTask
import org.jetbrains.compose.reload.orchestration.OrchestrationClientConnector.AwaitServerConnection
import java.net.InetSocketAddress
import java.net.ServerSocket
import kotlin.time.Duration.Companion.seconds

private val logger = createLogger()

/**
 * An 'awaiting' client is not trying to connect to a server.
 * Instead, it waits for a server to reach out to this client, establishing a connection (in UNO reverse card style).
 * See [OrchestrationServer.connectAwaitingClient].
 *
 * The server is expected to establish the connection by connecting to the provided [port].
 * Such deferred clients are useful for tools (like the IDE) which do want to be connected to the orchestration
 * as clients, once the application starts.
 */
@InternalHotReloadApi
public interface OrchestrationAwaitingClient : Task<OrchestrationClient>, AutoCloseable {
    public val port: Future<Int>
}

/**
 * See [OrchestrationAwaitingClient]:
 * Starts a deferred client waiting for an orchestration server to connect to it.
 */
@InternalHotReloadApi
public fun startOrchestrationAwaitingClient(role: OrchestrationClientRole): OrchestrationAwaitingClient {
    val port = Future<Int>()

    val thread = WorkerThread("Orchestration Dispatch")
    val task = launchTask("Orchestration Dispatch", thread.dispatcher) {
        invokeOnFinish { thread.shutdown() }
        invokeOnStop { reason -> port.completeExceptionally(reason) }

        val serverSocket = ServerSocket()
        invokeOnFinish { serverSocket.close() }

        serverSocket.bind(InetSocketAddress("127.0.0.1", 0))
        port.complete(serverSocket.localPort)

        val client = OrchestrationClient(role, AwaitServerConnection(serverSocket))
        client.connect().getOrThrow()
        client
    }

    return object : OrchestrationAwaitingClient, Task<OrchestrationClient> by task {
        override val port: Future<Int> = port
        override fun close() {
            task.stop()
        }
    }
}

/**
 * Creates a key, value pair which can be used as a JVM system property for an application process, which
 * is supposed to connect to the deferred client during startup.
 *
 * @param suspend: If true, then the JVM process will wait until the connection is established.
 */
@InternalHotReloadApi
public suspend fun OrchestrationAwaitingClient.toSystemProperty(suspend: Boolean = true): Pair<String, String> {
    return orchestrationClientPortProperty(port.awaitOrThrow()) to suspend.toString()
}

/**
 * Similar to [toSystemProperty], but returns a JVM argument. (e.g. "-Dkey=value")
 */
@InternalHotReloadApi
public suspend fun OrchestrationAwaitingClient.toJvmArg(): String {
    val (key, value) = toSystemProperty()
    return "-D$key=$value"
}

/**
 * Will ensure to connect to all deferred clients that are waiting for a connection and provided
 * as JVM system property
 */
@InternalHotReloadApi
public fun OrchestrationServer.connectAllAwaitingClients() {
    System.getProperties().forEach { (key, value) ->
        if (key !is String) return@forEach
        if (value !is String) return@forEach
        if (!key.startsWith(OrchestrationClientPortPropertyPrefix)) return@forEach
        val port = key.removePrefix(OrchestrationClientPortPropertyPrefix).toIntOrNull() ?: return@forEach
        val suspend = value.toBooleanStrictOrNull() ?: return@forEach

        val task = launchTask {
            logger.info("Connecting to deferred client on port $port, suspend=$suspend")
            val result = connectAwaitingClient(port)
            if (result.isFailure()) {
                logger.error("Failed to connect to deferred client on port $port", result.exception)
            }
        }

        if (suspend) {
            task.getBlocking(15.seconds)
        }
    }
}


/**
 * A 'template' property for all client ports which shall are awaiting to be connected to the orchestration.
 * e.g. if a client is waiting on port 2411, then a property 'compose.reload.orchestration.client.port.2411' will be available.
 * The provided 'boolean' value will instruct the application to suspend until the connection is established (if set to true)
 */
@InternalHotReloadApi
public const val OrchestrationClientPortPropertyPrefix: String = "compose.reload.orchestration.client."

@InternalHotReloadApi
public fun orchestrationClientPortProperty(port: Int): String =
    "$OrchestrationClientPortPropertyPrefix$port"
