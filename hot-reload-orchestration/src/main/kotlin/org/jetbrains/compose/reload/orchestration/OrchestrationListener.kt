/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.orchestration

import org.jetbrains.compose.reload.InternalHotReloadApi
import org.jetbrains.compose.reload.core.Future
import org.jetbrains.compose.reload.core.Queue
import org.jetbrains.compose.reload.core.Task
import org.jetbrains.compose.reload.core.Try
import org.jetbrains.compose.reload.core.WorkerThread
import org.jetbrains.compose.reload.core.awaitOrThrow
import org.jetbrains.compose.reload.core.complete
import org.jetbrains.compose.reload.core.completeExceptionally
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.debug
import org.jetbrains.compose.reload.core.dispatcher
import org.jetbrains.compose.reload.core.error
import org.jetbrains.compose.reload.core.getBlocking
import org.jetbrains.compose.reload.core.invokeOnFinish
import org.jetbrains.compose.reload.core.invokeOnStop
import org.jetbrains.compose.reload.core.isActive
import org.jetbrains.compose.reload.core.isSuccess
import org.jetbrains.compose.reload.core.launchTask
import org.jetbrains.compose.reload.core.toLeft
import org.jetbrains.compose.reload.orchestration.OrchestrationClientConnector.AwaitServerConnection
import java.net.InetSocketAddress
import java.net.ServerSocket
import kotlin.time.Duration.Companion.seconds

private val logger = createLogger()

/**
 * If an [OrchestrationListener] requested to suspend the application until a connection is established,
 * then this timeout will be used to wait for the connection.
 */
private val suspendTimeoutDuration = 15.seconds

/**
 * An [OrchestrationListener] will wait on an open port for an orchestration server to connect to it.
 * This can be used if a [OrchestrationClient] connection is desired before the application is even started.
 * One example use case here would be an IDE that would like to provide tooling for hot reload.
 * In this case the IDE can create a [OrchestrationListener] and establish one connection per application.
 *
 * Note: An application (with hot reload) has to be started, providing the [orchestrationListenerPortProperty]
 * to automatically connect to the listener and establish the connection.
 */
@InternalHotReloadApi
public interface OrchestrationListener : Task<Unit>, AutoCloseable {
    public val port: Future<Int>
    public val connections: Queue<Try<OrchestrationClient>>
}

/**
 * See [OrchestrationListener]:
 * Starts a deferred client waiting for an orchestration server to connect to it.
 */
@InternalHotReloadApi
public fun startOrchestrationListener(role: OrchestrationClientRole): OrchestrationListener {
    val port = Future<Int>()
    val connections = Queue<Try<OrchestrationClient>>()

    val thread = WorkerThread("Orchestration Listener")
    val task = launchTask("Orchestration Listener", thread.dispatcher) {
        invokeOnFinish { thread.shutdown() }
        invokeOnStop { reason -> port.completeExceptionally(reason) }

        val serverSocket = ServerSocket()
        invokeOnFinish { serverSocket.close() }

        serverSocket.bind(InetSocketAddress("127.0.0.1", 0))
        port.complete(serverSocket.localPort)

        while (isActive()) {
            val client = OrchestrationClient(role, AwaitServerConnection(serverSocket))
            invokeOnFinish { client.close() }
            val connection = client.connect()
            if (isActive()) {
                connections.send(if (connection.isSuccess()) client.toLeft() else connection)
            }
        }
    }

    return object : OrchestrationListener, Task<Unit> by task {
        override val port: Future<Int> = port
        override val connections: Queue<Try<OrchestrationClient>> = connections

        override fun close() {
            task.stop()
        }
    }
}

/**
 * Creates a key, value pair which can be used as a JVM system property or environment variable
 * for an application process, which is supposed to connect to the client during startup.
 *
 * @param suspend: If true, then the JVM process will wait until the connection is established.
 */
@InternalHotReloadApi
public suspend fun OrchestrationListener.toSystemProperty(suspend: Boolean = true): Pair<String, String> {
    return orchestrationListenerPortProperty(port.awaitOrThrow()) to suspend.toString()
}

/**
 * Similar to [toSystemProperty], but returns a JVM argument. (e.g. "-Dkey=value")
 */
@InternalHotReloadApi
public suspend fun OrchestrationListener.toJvmArg(): String {
    val (key, value) = toSystemProperty()
    return "-D$key=$value"
}

/**
 * Will ensure to connect to all deferred clients that are waiting for a connection and provided
 * as JVM system property or environment variable.
 */
@InternalHotReloadApi
public fun OrchestrationServer.connectAllOrchestrationListeners() {
    try {
        (System.getProperties() + System.getenv()).entries.distinctBy { (key, _) -> key }.forEach { (key, value) ->
            if (key !is String) return@forEach
            if (value !is String) return@forEach
            if (!key.startsWith(OrchestrationListenerPortPropertyPrefix)) return@forEach
            val port = key.removePrefix(OrchestrationListenerPortPropertyPrefix).toIntOrNull() ?: return@forEach
            val suspend = value.toBooleanStrictOrNull() ?: return@forEach


            try {
                val task = launchTask {
                    logger.debug("Connecting to orchestration listener on port $port, suspend=$suspend")
                    if (!connectClient(port)) {
                        logger.error("Failed to connect to orchestration listener on port $port")
                    }
                }

                if (suspend) {
                    task.getBlocking(suspendTimeoutDuration)
                }
            } catch (t: Throwable) {
                logger.error("Failed to connect to orchestration listener on port $port", t)
            }

        }
    } catch (t: Throwable) {
        logger.error("Failed to connect to orchestration listeners", t)
    }
}


/**
 * A 'template' property for all client ports which shall are awaiting to be connected to the orchestration.
 * e.g. if a client is waiting on port 2411, then a property 'compose.reload.orchestration.listener.port.2411' will be available.
 * The provided 'boolean' value will instruct the application to suspend until the connection is established (if set to true)
 */
@InternalHotReloadApi
public const val OrchestrationListenerPortPropertyPrefix: String = "compose.reload.orchestration.listener.port."

@InternalHotReloadApi
public fun orchestrationListenerPortProperty(port: Int): String =
    "$OrchestrationListenerPortPropertyPrefix$port"
