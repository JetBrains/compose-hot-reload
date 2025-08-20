/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.orchestration

import org.jetbrains.compose.reload.core.Bus
import org.jetbrains.compose.reload.core.Future
import org.jetbrains.compose.reload.core.StoppedException
import org.jetbrains.compose.reload.core.Task
import org.jetbrains.compose.reload.core.Update
import org.jetbrains.compose.reload.core.WorkerThread
import org.jetbrains.compose.reload.core.complete
import org.jetbrains.compose.reload.core.completeExceptionally
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.exceptionOrNull
import org.jetbrains.compose.reload.core.getBlocking
import org.jetbrains.compose.reload.core.getOrThrow
import org.jetbrains.compose.reload.core.invokeOnFinish
import org.jetbrains.compose.reload.core.invokeOnStop
import org.jetbrains.compose.reload.core.isActive
import org.jetbrains.compose.reload.core.launchOnFinish
import org.jetbrains.compose.reload.core.launchOnStop
import org.jetbrains.compose.reload.core.launchTask
import org.jetbrains.compose.reload.core.stopNow
import org.jetbrains.compose.reload.core.trace
import org.jetbrains.compose.reload.core.withThread
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ClientConnected
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ClientDisconnected
import org.jetbrains.compose.reload.orchestration.OrchestrationPackage.Ack
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.time.Duration.Companion.seconds

public fun startOrchestrationServer(): OrchestrationServer {
    val server = OrchestrationServer()

    launchTask("startOrchestrationServer") {
        server.start()
        server.port.await().getOrThrow()
    }.getBlocking(15.seconds).getOrThrow()

    return server
}

public interface OrchestrationServer : OrchestrationHandle {
    public suspend fun bind()
    public suspend fun start()
}

public fun OrchestrationServer(): OrchestrationServer {
    val bind = Future<Unit>()
    val port = Future<Int>()

    val start = Future<Unit>()
    val messages = Bus<OrchestrationMessage>()
    val states = OrchestrationServerStates()

    val task = launchTask("OrchestrationServer") {
        invokeOnFinish { bind.completeExceptionally(it.exceptionOrNull() ?: StoppedException()) }
        invokeOnFinish { port.completeExceptionally(it.exceptionOrNull() ?: StoppedException()) }

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

                val client = launchClient(clientSocket, messages, states)
                invokeOnFinish { client.stop() }
            }
        }
    }

    return object : OrchestrationServer, Task<Unit> by task {
        override val messages = messages
        override val port: Future<Int> = port
        override val states = states

        override suspend fun send(message: OrchestrationMessage) {
            messages.send(message)
        }

        override suspend fun <T : OrchestrationState?> update(
            key: OrchestrationStateKey<T>, update: (T) -> T
        ): Update<T> {
            return states.update(key, update)
        }

        /* Server will always be able to update the state, as owner */
        override suspend fun <T : OrchestrationState?> tryUpdate(
            key: OrchestrationStateKey<T>, update: (T) -> T
        ): Update<T> = update(key, update)

        override suspend fun bind() {
            bind.complete(Unit)
            port.await()
        }

        override suspend fun start() {
            bind()
            start.complete(Unit)
        }
    }
}

private fun launchClient(
    socket: Socket,
    messages: Bus<OrchestrationMessage>,
    states: OrchestrationServerStates,
): Task<*> = launchTask("Client Connection") {
    val logger = createLogger()
    val writer = WorkerThread("Orchestration Server: Writer")
    val reader = WorkerThread("Orchestration Server: Reader")


    val io = OrchestrationIO(socket, writer, reader)
    launchOnStop { io.close() }
    launchOnFinish { io.close() }

    /* Check protocol magic number */
    checkMagicNumberOrThrow(io.readInt())

    /* Read the client protocol version */
    val clientProtocolVersion = io.readInt()
    logger.trace { "client protocol version: $clientProtocolVersion" }

    /* Write protocol magic number and the servers protocol version */
    io writeInt ORCHESTRATION_PROTOCOL_MAGIC_NUMBER
    io writeInt OrchestrationVersion.current.intValue

    /* We expect any given client to start with a proper introduction */
    val clientIntroduction = io.readPackage()
    if (clientIntroduction !is OrchestrationPackage.Introduction) {
        throw OrchestrationIOException("Unexpected introduction: $clientIntroduction")
    }

    /* The introduction was successful, the client is connected */
    val connected = ClientConnected(
        clientId = clientIntroduction.clientId,
        clientRole = clientIntroduction.clientRole,
        clientPid = clientIntroduction.clientPid
    )

    /* Write the 'client connected' message into the bus and to the client as well */
    messages.send(connected)
    io writePackage connected

    launchOnFinish {
        messages send ClientDisconnected(connected.clientId, connected.clientRole)
    }

    /* State streaming: If requested, all updates to a given state will be sent to the client */
    val launchedStateStreams = hashSetOf<OrchestrationStateId<*>>()

    fun launchStateStreaming(id: OrchestrationStateId<*>) = subtask("State Stream: '$id'") {
        states.getEncodedState(id).collect { value ->
            io writePackage OrchestrationStateValue(id, value)
        }
    }

    fun launchStateStreamingIfNecessary(id: OrchestrationStateId<*>) {
        if (!launchedStateStreams.add(id)) return
        launchStateStreaming(id)
    }

    /* Writer loop: Take elements from the bus and write them to the OrchestrationIO */
    subtask("Writer") {
        messages.collect { pkg ->
            io writePackage pkg
        }
    }

    /* Reader loop: Read messages, push them through the Bus and send back an 'Ack' */
    subtask("Reader") {
        while (isActive()) {
            val pkg = io.readPackage() ?: stopNow()
            when (pkg) {
                is OrchestrationStateRequest -> launchStateStreamingIfNecessary(pkg.stateId)

                is OrchestrationStateUpdate -> states.withLock {
                    val accepted = states.update(pkg.stateId, pkg.expectedValue, pkg.updatedValue)
                    io writePackage OrchestrationStateUpdate.Response(accepted)
                }

                is OrchestrationMessage -> {
                    messages.send(pkg)
                    io.writePackage(Ack(pkg.messageId))
                }

                else -> continue
            }
        }
    }

    /* Update the client connections state */
    states.update(OrchestrationConnectionsState) { state ->
        state.withConnection(connected.clientId, connected.clientRole, connected.clientPid)
    }

    invokeOnFinish {
        states.update(OrchestrationConnectionsState) { state ->
            state.withoutConnection(connected.clientId)
        }
    }
}
