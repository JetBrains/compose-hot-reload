/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.orchestration

import org.jetbrains.compose.reload.core.Actor
import org.jetbrains.compose.reload.core.Bus
import org.jetbrains.compose.reload.core.Future
import org.jetbrains.compose.reload.core.Queue
import org.jetbrains.compose.reload.core.StoppedException
import org.jetbrains.compose.reload.core.Task
import org.jetbrains.compose.reload.core.Update
import org.jetbrains.compose.reload.core.WorkerThread
import org.jetbrains.compose.reload.core.awaitOrThrow
import org.jetbrains.compose.reload.core.complete
import org.jetbrains.compose.reload.core.completeExceptionally
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.debug
import org.jetbrains.compose.reload.core.dispatcherImmediate
import org.jetbrains.compose.reload.core.error
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
import org.jetbrains.compose.reload.core.withType
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ClientConnected
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ClientDisconnected
import org.jetbrains.compose.reload.orchestration.OrchestrationPackage.Ack
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.time.Duration.Companion.seconds


private val logger = createLogger<OrchestrationServer>()

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

    /**
     * See [OrchestrationListener]:
     * Such clients listen for incoming connections at [listenerPort] and expect to be connected by the server.
     * This might be useful when tools want to be connected to the orchestration as client, before
     * the orchestration is even started (e.g. an IDE).
     */
    public suspend fun connectClient(listenerPort: Int): Boolean
}

public fun OrchestrationServer(): OrchestrationServer {
    val bind = Future<Unit>()
    val port = Future<Int>()

    val start = Future<Unit>()
    val messages = Bus<OrchestrationPackage>()
    val states = OrchestrationServerStates()

    /**
     * Represents an actor who attempts to connect to clients listening at a given port.
     */
    val connectActor = Actor</* Port */ Int, Boolean>()

    val task = launchTask<Unit>("OrchestrationServer") {
        invokeOnFinish { bind.completeExceptionally(it.exceptionOrNull() ?: StoppedException()) }
        invokeOnFinish { port.completeExceptionally(it.exceptionOrNull() ?: StoppedException()) }

        val serverThread = WorkerThread("Orchestration Server")
        launchOnFinish { serverThread.shutdown().await() }

        subtask("Listen for incoming connections", serverThread.dispatcherImmediate) {
            val serverSocket = ServerSocket()
            invokeOnStop { serverSocket.close() }

            bind.await()
            serverSocket.bind(InetSocketAddress("127.0.0.1", 0))
            port.complete(serverSocket.localPort)

            start.await()
            while (isActive()) {
                val clientSocket = serverSocket.accept()
                clientSocket.setOrchestrationDefaults()

                val client = launchClient(OrchestrationIO(clientSocket), messages, states)
                invokeOnFinish { client.stop() }
            }
        }

        subtask("Connect to clients") {
            val serverPort = port.awaitOrThrow()

            connectActor.process { port ->
                logger.debug("Connecting to client on port '$port'")
                val reader = OrchestrationIO.newReaderThread()
                val writer = OrchestrationIO.newWriterThread()
                try {
                    withThread(writer) {
                        val socket = Socket("127.0.0.1", port)
                        socket.setOrchestrationDefaults()
                        val io = OrchestrationIO(socket, writer = writer, reader = reader)

                        io.writeInt(ORCHESTRATION_PROTOCOL_MAGIC_NUMBER)
                        io.writeInt(OrchestrationVersion.current.intValue)
                        io.writeInt(serverPort)

                        val client = launchClient(io, messages, states)
                        invokeOnFinish { client.stop() }
                        true
                    }
                } catch (t: Throwable) {
                    logger.error("Failed to connect to client on port '$port'", t)
                    reader.close()
                    writer.close()
                    false
                }
            }
        }
    }

    return object : OrchestrationServer, Task<Unit> by task {
        override val messages = messages.withType<OrchestrationMessage>()
        override val port: Future<Int> = port
        override val states = states

        override suspend fun connectClient(listenerPort: Int): Boolean {
            return connectActor(listenerPort)
        }

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
    io: OrchestrationIO,
    messages: Bus<OrchestrationPackage>,
    states: OrchestrationServerStates,
): Task<*> = launchTask("Client Connection") {
    val logger = createLogger()

    launchOnStop { io.close() }
    launchOnFinish { io.close() }

    /* Check protocol magic number */
    checkMagicNumberOrThrow(io.readInt())

    /* Read the client protocol version */
    val clientProtocolVersion = OrchestrationVersion(io.readInt())
    logger.trace { "client protocol version: $clientProtocolVersion" }

    /* Write protocol magic number and the servers protocol version */
    io writeInt ORCHESTRATION_PROTOCOL_MAGIC_NUMBER
    io writeInt OrchestrationVersion.current.intValue

    /* We expect any given client to start with a proper introduction */
    val clientIntroduction = io.readPackage()
    if (clientIntroduction !is OrchestrationPackage.Introduction) {
        throw OrchestrationIOException("Unexpected introduction: $clientIntroduction")
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
        val queue = Queue<OrchestrationPackage>()

        subtask {
            val clientConnected = ClientConnected(
                clientId = clientIntroduction.clientId,
                clientRole = clientIntroduction.clientRole,
                clientPid = clientIntroduction.clientPid
            )

            /**
             * We consider the client to be connected precisely at this moment, where
             * we know that the 'queue' above is wired-up and each message from [messages] will be received.
             * We know that a subtask will be dispatched to the end of the [org.jetbrains.compose.reload.core.reloadMainThread],
             * which ensures that the 'messages.collect {}' below is active already
             */
            messages.send(clientConnected)

            /**
             * Cleanup: Since we announced the [clientConnected] state, we have to make sure that the 'disconnect' is
             * sent on finish.
             */
            launchOnFinish {
                messages send ClientDisconnected(clientConnected.clientId, clientConnected.clientRole)
            }


            /* Update the client connections state */
            states.update(OrchestrationConnectionsState) { state ->
                state.withConnection(clientConnected.clientId, clientConnected.clientRole, clientConnected.clientPid)
            }

            /**
             * Cleanup: Remove the client from the [OrchestrationConnectionsState] once this task finishes
             */
            invokeOnFinish {
                states.update(OrchestrationConnectionsState) { state ->
                    state.withoutConnection(clientConnected.clientId)
                }
            }

            /**
             * We enqueued the [clientConnected] message; the new client's stream will start exactly with this [clientConnected]
             * message. Messages prior to [clientConnected] are supposed to be ignored.
             */
            while (isActive()) {
                if (queue.receive() == clientConnected) {
                    io.writePackage(clientConnected)
                    break
                }
            }

            while (isActive()) {
                io writePackage queue.receive()
            }
        }

        messages.collect { message ->
            /* Do not forward opaque messages if the client is not guaranteed to support them */
            if (message is OpaqueOrchestrationMessage && !clientProtocolVersion.supportsOpaqueMessages) {
                return@collect
            }

            /* Do not send the messages to clients who do not know/care about newer messages */
            if (message is OrchestrationMessage && message.availableSinceVersion > clientProtocolVersion) {
                return@collect
            }

            queue.send(message)
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

                is OpaqueOrchestrationMessage -> {
                    messages.send(pkg)
                    io.writePackage(Ack(null))
                }

                is OrchestrationMessage -> {
                    messages.send(pkg)
                    io.writePackage(Ack(pkg.messageId))
                }

                else -> continue
            }
        }
    }
}
