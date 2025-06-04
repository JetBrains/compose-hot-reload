/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.orchestration

import org.jetbrains.compose.reload.core.Broadcast
import org.jetbrains.compose.reload.core.Future
import org.jetbrains.compose.reload.core.StoppedException
import org.jetbrains.compose.reload.core.Task
import org.jetbrains.compose.reload.core.Try
import org.jetbrains.compose.reload.core.WorkerThread
import org.jetbrains.compose.reload.core.await
import org.jetbrains.compose.reload.core.collect
import org.jetbrains.compose.reload.core.complete
import org.jetbrains.compose.reload.core.completeExceptionally
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.isActive
import org.jetbrains.compose.reload.core.launchTask
import org.jetbrains.compose.reload.core.withThread
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ClientConnected
import org.jetbrains.compose.reload.orchestration.OrchestrationPackage.Ack
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

public fun startOrchestrationServer(): OrchestrationServer {
    val bind = Future<Unit>()
    val port = Future<Int>()

    val start = Future<Unit>()

    val messages = Broadcast<OrchestrationMessage>()

    val task = launchTask {
        invokeOnFinish { bind.completeExceptionally(it ?: StoppedException()) }
        invokeOnFinish { port.completeExceptionally(it ?: StoppedException()) }


        val serverThread = WorkerThread("Orchestration Server")
        launchOnFinish { serverThread.shutdown().await() }

        withThread(serverThread, true) {
            val serverSocket = ServerSocket()
            invokeOnStop { serverSocket.close() }

            bind.await()
            serverSocket.bind(InetSocketAddress("127.0.0.1", 0))
            port.complete(serverSocket.localPort)

            start.await()
            while (isActive()) {
                val clientSocket = serverSocket.accept()
                clientSocket.setOrchestrationDefaults()

                val client = launchClient(clientSocket, messages)
                invokeOnFinish { client.stop() }
            }
        }
    }

    return object : OrchestrationServer {
        override val messages = messages
        override val port: Future<Int> = port

        override suspend fun send(message: OrchestrationMessage) {
            messages.send(message)
        }

        override suspend fun isActive(): Boolean {
            return task.isActive
        }

        override suspend fun close(): Boolean {
            return task.stop()
        }

        override suspend fun bind(): Try<Int> {
            bind.complete(Unit)
            return port.await()
        }
    }
}

private fun launchClient(
    socket: Socket, messages: Broadcast<OrchestrationMessage>,
): Task<*> = launchTask {
    val logger = createLogger()
    val writer = WorkerThread("Orchestration Server: Writer")
    val reader = WorkerThread("Orchestration Server: Reader")


    val io = OrchestrationIO(socket, writer, reader)
    launchOnStop { io.close() }
    launchOnFinish { io.close() }

    /* Check protocol magic number */
    checkMagicNumberOrThrow(io.readInt())

    val clientProtocolVersion = io.readInt()
    logger.debug("client protocol version: $clientProtocolVersion")

    io writeInt ORCHESTRATION_PROTOCOL_MAGIC_NUMBER
    io writeInt OrchestrationProtocolVersion.current.intValue

    val clientIntroduction = io.readPackage()
    if (clientIntroduction !is OrchestrationPackage.Introduction) {
        throw OrchestrationIOException("Unexpected introduction: $clientIntroduction")
    }

    io writePackage ClientConnected(
        clientId = clientIntroduction.clientId,
        clientRole = clientIntroduction.clientRole,
        clientPid = clientIntroduction.clientPid
    )

    launch {
        messages.collect { pkg ->
            io.writePackage(pkg)
        }
    }

    launch {
        while (isActive) {
            val pkg = io.readPackage()
            if (pkg !is OrchestrationMessage) continue
            messages.send(pkg)
            io.writePackage(Ack(pkg.messageId))
        }
    }
}

public interface OrchestrationServer : OrchestrationHandle {
    public val port: Future<Int>
    public suspend fun bind(): Try<Int>
}
