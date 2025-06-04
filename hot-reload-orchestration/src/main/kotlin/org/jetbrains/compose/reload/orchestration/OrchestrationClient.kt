/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

@file:OptIn(ExperimentalUuidApi::class)

package org.jetbrains.compose.reload.orchestration

import org.jetbrains.compose.reload.core.Actor
import org.jetbrains.compose.reload.core.Broadcast
import org.jetbrains.compose.reload.core.Future
import org.jetbrains.compose.reload.core.HotReloadEnvironment
import org.jetbrains.compose.reload.core.Queue
import org.jetbrains.compose.reload.core.StoppedException
import org.jetbrains.compose.reload.core.Try
import org.jetbrains.compose.reload.core.WorkerThread
import org.jetbrains.compose.reload.core.await
import org.jetbrains.compose.reload.core.complete
import org.jetbrains.compose.reload.core.completeExceptionally
import org.jetbrains.compose.reload.core.launchTask
import org.jetbrains.compose.reload.core.withThread
import org.jetbrains.compose.reload.orchestration.OrchestrationPackage.Introduction
import org.slf4j.LoggerFactory
import java.io.Serializable
import java.net.Socket
import java.util.UUID
import kotlin.uuid.ExperimentalUuidApi


public fun OrchestrationClient(role: OrchestrationClientRole): OrchestrationClient? {
    val port = HotReloadEnvironment.orchestrationPort ?: return null
    return connectOrchestrationClient(role, port = port)
}

public fun connectOrchestrationClient(clientRole: OrchestrationClientRole, port: Int): OrchestrationClient {
    val logger = LoggerFactory.getLogger("OrchestrationClient($clientRole, $port)")

    val connect = Future<Unit>()
    val isConnected = Future<Unit>()

    val clientId = OrchestrationClientId.random()
    val sendActor = Actor<OrchestrationMessage, Unit>()
    val receiveBroadcast = Broadcast<OrchestrationMessage>()
    val ackQueue = Queue<OrchestrationPackage.Ack>()


    val task = launchTask {
        connect.await()

        val writer = WorkerThread("Orchestration IO: Writer")
        val reader = WorkerThread("Orchestration IO: Reader")

        launchOnFinish { error ->
            sendActor.close(error)
            isConnected.completeExceptionally(error ?: StoppedException())

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
        io.writeInt(OrchestrationProtocolVersion.current.intValue) /* Protocol Version */

        /* Check protocol magic number */
        checkMagicNumberOrThrow(io.readInt())

        val serverProtocolVersion = io.readInt()
        logger.debug("OrchestrationServer protocol version: $serverProtocolVersion")

        /* Send Handshake, expect 'ClientConnected' response */
        io.writePackage(Introduction(clientId, clientRole, ProcessHandle.current().pid()))
        val response = io.readPackage()
        if (response !is OrchestrationMessage.ClientConnected || response.clientId != clientId) {
            error("Unexpected response: $response")
        }

        /* Handshake was OK: We're officially connected */
        isConnected.complete(Unit)

        /* Launch sequential writer coroutine */
        launch {
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
        launch {
            while (isActive) {
                val pkg = io.readPackage()
                if (pkg is OrchestrationPackage.Ack) ackQueue.send(pkg)
                if (pkg !is OrchestrationMessage) continue
                receiveBroadcast.send(pkg)
            }
        }
    }

    return object : OrchestrationClient {
        override val clientId: OrchestrationClientId = clientId
        override val clientRole: OrchestrationClientRole = clientRole

        override suspend fun connect(): Try<Unit> {
            connect.complete(Unit)
            return isConnected.await()
        }

        override val messages: Broadcast<OrchestrationMessage> = receiveBroadcast

        override suspend fun send(message: OrchestrationMessage) {
            sendActor(message)
        }

        override suspend fun isActive(): Boolean {
            return task.isActive
        }

        override suspend fun close(): Boolean {
            return task.stop()
        }
    }
}

public interface OrchestrationHandle {
    public val messages: Broadcast<OrchestrationMessage>
    public suspend fun send(message: OrchestrationMessage)
    public suspend fun isActive(): Boolean
    public suspend fun close(): Boolean
}

public interface OrchestrationClient : OrchestrationHandle {
    public val clientId: OrchestrationClientId
    public val clientRole: OrchestrationClientRole
    public suspend fun connect(): Try<Unit>
}

public data class OrchestrationClientId(val value: String) : Serializable {
    public companion object {
        public fun random(): OrchestrationClientId = OrchestrationClientId(UUID.randomUUID().toString())
    }
}
