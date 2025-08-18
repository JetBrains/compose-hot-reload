/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

@file:OptIn(ExperimentalUuidApi::class)

package org.jetbrains.compose.reload.orchestration

import org.jetbrains.compose.reload.core.Actor
import org.jetbrains.compose.reload.core.Broadcast
import org.jetbrains.compose.reload.core.Bus
import org.jetbrains.compose.reload.core.CompletableFuture
import org.jetbrains.compose.reload.core.Future
import org.jetbrains.compose.reload.core.HotReloadEnvironment
import org.jetbrains.compose.reload.core.Queue
import org.jetbrains.compose.reload.core.StoppedException
import org.jetbrains.compose.reload.core.Task
import org.jetbrains.compose.reload.core.Try
import org.jetbrains.compose.reload.core.Update
import org.jetbrains.compose.reload.core.WorkerThread
import org.jetbrains.compose.reload.core.awaitOrThrow
import org.jetbrains.compose.reload.core.complete
import org.jetbrains.compose.reload.core.completeExceptionally
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.currentCoroutineContext
import org.jetbrains.compose.reload.core.exceptionOrNull
import org.jetbrains.compose.reload.core.invokeOnError
import org.jetbrains.compose.reload.core.isActive
import org.jetbrains.compose.reload.core.isFailure
import org.jetbrains.compose.reload.core.launchOnFinish
import org.jetbrains.compose.reload.core.launchOnStop
import org.jetbrains.compose.reload.core.launchTask
import org.jetbrains.compose.reload.core.stopNow
import org.jetbrains.compose.reload.core.toLeft
import org.jetbrains.compose.reload.core.trace
import org.jetbrains.compose.reload.core.warn
import org.jetbrains.compose.reload.core.withThread
import org.jetbrains.compose.reload.orchestration.OrchestrationPackage.Introduction
import java.io.Serializable
import java.net.Socket
import java.util.UUID
import kotlin.uuid.ExperimentalUuidApi

public fun OrchestrationClient(role: OrchestrationClientRole): OrchestrationClient? {
    val port = HotReloadEnvironment.orchestrationPort ?: return null
    return OrchestrationClient(role, port = port)
}

public suspend fun connectOrchestrationClient(role: OrchestrationClientRole, port: Int): Try<OrchestrationClient> {
    val client = OrchestrationClient(role, port)
    val connected = client.connect()
    return if (connected.isFailure()) connected
    else client.toLeft()
}

public interface OrchestrationClient : OrchestrationHandle {
    public val clientId: OrchestrationClientId
    public val clientRole: OrchestrationClientRole
    public suspend fun connect(): Try<Unit>
}

public data class OrchestrationClientId(val value: String) : Serializable {
    public companion object {
        public fun random(): OrchestrationClientId = OrchestrationClientId(UUID.randomUUID().toString())
        internal const val serialVersionUID: Long = 0L
    }
}

public fun OrchestrationClient(clientRole: OrchestrationClientRole, port: Int): OrchestrationClient {
    val logger = createLogger("OrchestrationClient($clientRole, $port)")

    val connect = Future<Unit>()
    val isConnected = Future<Unit>()

    val clientId = OrchestrationClientId.random()
    val sendActor = Actor<OrchestrationMessage, Unit>()

    val stateRequests = Queue<OrchestrationStateRequest>()
    val stateUpdatesActor = Actor<OrchestrationStateUpdate, Boolean>()
    val states = OrchestrationClientStates(stateRequests)

    val receiveBroadcast = Bus<OrchestrationMessage>()
    val ackQueue = Queue<OrchestrationPackage.Ack>()

    val task = launchTask<Unit>("OrchestrationClient($clientRole, $port)") {
        connect.await()
        invokeOnError { isConnected.completeExceptionally(it) }

        val writer = WorkerThread("Orchestration IO: Writer")
        val reader = WorkerThread("Orchestration IO: Reader")

        launchOnFinish { result ->
            sendActor.close(result.exceptionOrNull())
            isConnected.completeExceptionally(result.exceptionOrNull() ?: StoppedException())

            writer.shutdown().await()
            reader.shutdown().await()
        }

        val socket = withThread(writer) {
            val socket = Socket("127.0.0.1", port)
            socket.setOrchestrationDefaults()
            socket
        }

        val io = OrchestrationIO(socket, writer, reader)
        launchOnStop { io.close() }
        launchOnFinish { io.close() }

        io.writeInt(ORCHESTRATION_PROTOCOL_MAGIC_NUMBER) /* Magic Number */
        io.writeInt(OrchestrationVersion.current.intValue) /* Protocol Version */

        /* Check protocol magic number */
        checkMagicNumberOrThrow(io.readInt())

        val serverProtocolVersion = OrchestrationVersion(io.readInt())
        logger.trace { "OrchestrationServer protocol version: $serverProtocolVersion" }

        /* Send Handshake, expect 'ClientConnected' response */
        io.writePackage(Introduction(clientId, clientRole, ProcessHandle.current().pid()))
        val response = io.readPackage()
        if (response !is OrchestrationMessage.ClientConnected || response.clientId != clientId) {
            error("Unexpected response: $response")
        }

        /* Handshake was OK: We're officially connected */
        isConnected.complete(Unit)

        subtask("Request States") {
            while (isActive()) {
                val stateRequest = stateRequests.receive()
                /* We return 'null' as state if the server does not support state sync */
                if (!serverProtocolVersion.supportsStates) {
                    logger.warn("The server does not support state hosting ($serverProtocolVersion)")
                    states.update(OrchestrationStateValue(stateRequest.stateId, null))
                    return@subtask
                }

                io writePackage stateRequest
            }
        }

        var pendingUpdate: OrchestrationStateUpdate? = null
        var pendingUpdateAccepted: CompletableFuture<Boolean>? = null
        subtask("Update States") {
            stateUpdatesActor.process { update ->
                /* We accept any state update if the server does not support states*/
                if (!serverProtocolVersion.supportsStates) {
                    logger.warn("The server does not support state hosting ($serverProtocolVersion)")
                    states.update(update.stateId, update.updatedValue)
                    return@process true
                }

                try {
                    pendingUpdate = update
                    pendingUpdateAccepted = Future<Boolean>()
                    io writePackage update
                    pendingUpdateAccepted!!.awaitOrThrow()
                } finally {
                    pendingUpdate = null
                    pendingUpdateAccepted = null
                }
            }
        }

        /* Launch sequential writer coroutine */
        subtask("Writer") {
            sendActor.process { message ->
                /* Get dispatch and write it as package */
                io.writePackage(message)

                /* Await the ack from the server */
                val ack = ackQueue.receive()

                check(ack.messageId == message.messageId) {
                    "Unexpected ack '${ack.messageId}'"
                }
            }
        }

        /* Launch sequential reader coroutine */
        subtask("Reader") {
            while (isActive()) {
                val pkg = io.readPackage() ?: stopNow()
                when (pkg) {
                    is OrchestrationPackage.Ack -> ackQueue.send(pkg)
                    is OrchestrationMessage -> receiveBroadcast.send(pkg)
                    is OrchestrationStateValue -> states.update(pkg)
                    is OrchestrationStateUpdate.Response -> {
                        val pendingUpdate = pendingUpdate ?: error("No pending state update")
                        if (pkg.accepted) states.update(
                            OrchestrationStateValue(
                                pendingUpdate.stateId,
                                pendingUpdate.updatedValue
                            )
                        )
                        pendingUpdateAccepted?.complete(pkg.accepted)
                    }

                    else -> continue
                }
            }
        }
    }

    return object : OrchestrationClient, Task<Unit> by task {
        override val port: Future<Int> = Future(port)
        override val clientId: OrchestrationClientId = clientId
        override val clientRole: OrchestrationClientRole = clientRole
        override val messages: Broadcast<OrchestrationMessage> = receiveBroadcast
        override val states = states

        override suspend fun connect(): Try<Unit> {
            connect.complete(Unit)
            return isConnected.await()
        }

        override suspend fun send(message: OrchestrationMessage) {
            sendActor.invoke(message)
        }

        override suspend fun <T : OrchestrationState?> update(
            key: OrchestrationStateKey<T>, update: (T) -> T
        ): Update<T> {
            while (currentCoroutineContext().isActive()) {
                val update = tryUpdate(key, update)
                if (update != null) return update
            }

            throw StoppedException()
        }

        override suspend fun <T : OrchestrationState?> tryUpdate(
            key: OrchestrationStateKey<T>, update: (T) -> T
        ): Update<T>? {
            val encodedUpdate = states.encodeUpdate(key, update)
            return if (stateUpdatesActor(encodedUpdate.encoded)) {
                Update(encodedUpdate.previousState, encodedUpdate.updatedState)
            } else null
        }
    }
}
