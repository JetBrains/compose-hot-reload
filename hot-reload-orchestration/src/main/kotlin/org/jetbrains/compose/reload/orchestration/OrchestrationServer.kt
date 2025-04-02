/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.orchestration

import org.jetbrains.compose.reload.core.Disposable
import org.jetbrains.compose.reload.core.submitSafe
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ClientConnected
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock

public interface OrchestrationServer : OrchestrationHandle

public fun startOrchestrationServer(): OrchestrationServer {
    val serverSocket = ServerSocket()
    serverSocket.bind(InetSocketAddress("127.0.0.1", 0))

    val logger = LoggerFactory.getLogger("OrchestrationServer(${serverSocket.localPort})")
    logger.debug("listening on port: ${serverSocket.localPort}")

    val server = OrchestrationServerImpl(serverSocket, logger)
    server.start()

    return server
}

private class OrchestrationServerImpl(
    private val serverSocket: ServerSocket,
    private val logger: Logger
) : OrchestrationServer {
    private val isClosed = AtomicBoolean(false)
    private val isActive get() = !isClosed.get()
    private val lock = ReentrantLock()
    private val listeners = mutableListOf<(OrchestrationMessage) -> Unit>()
    private val closeListeners = mutableListOf<() -> Unit>()
    private val clients = mutableListOf<Client>()

    private inner class Client(
        private val socket: Socket,
        val id: UUID,
        val role: OrchestrationClientRole,
        val input: ObjectInputStream,
        private val output: ObjectOutputStream
    ) : AutoCloseable {

        private val writingThread = Executors.newSingleThreadExecutor { runnable ->
            thread(
                name = "Orchestration Client Writer (${socket.remoteSocketAddress})",
                isDaemon = true,
                start = false
            ) {
                runnable.run()
            }
        }

        fun write(message: OrchestrationMessage): Future<Unit> {
            return writingThread.submitSafe {
                try {
                    output.writeObject(message)
                    output.flush()
                } catch (_: Throwable) {
                    lock.withLock { clients.remove(this) }
                    logger.debug("Closing client: '$this'")
                    close()
                }
            }
        }

        override fun close() {
            writingThread.shutdownNow()
            if (!writingThread.awaitTermination(1, TimeUnit.SECONDS)) {
                logger.warn("'writerThread' did not finish gracefully in 1 second '$this'")
            }

            socket.close()
            sendMessage(OrchestrationMessage.ClientDisconnected(id, role))
        }

        override fun toString(): String {
            return "Client [$role] (${socket.remoteSocketAddress})"
        }
    }

    override val port: Int
        get() = serverSocket.localPort

    override fun invokeWhenMessageReceived(action: (OrchestrationMessage) -> Unit): Disposable {
        val registration = Throwable()

        val safeListener: (OrchestrationMessage) -> Unit = { message ->
            try {
                action(message)
            } catch (t: Throwable) {
                assert(false) { throw t }
                logger.error("Failed invoking orchestration listener", t)
                logger.error("Failing listener was registered at:", registration)
            }
        }
        lock.withLock { listeners.add(safeListener) }
        return Disposable {
            lock.withLock { listeners.remove(safeListener) }
        }
    }

    override fun invokeWhenClosed(action: () -> Unit) {
        lock.withLock {
            if (isClosed.get()) action()
            else closeListeners.add(action)
        }
    }

    override fun sendMessage(message: OrchestrationMessage): Future<Unit> = orchestrationThread.submitSafe {
        /* Send the message to all, currently connected clients */
        val clients = lock.withLock { clients.toList() }
        clients.forEach { client -> client.write(message) }

        /*
        Send the message to all message listeners:
        Clients will get an 'echo' from the server, once the server has handled the message.
        When the server sends a message, the 'echo' will be sent once all clients received the message.
         */
        invokeMessageListeners(message)
    }

    fun start() {
        thread(name = "Orchestration Server", isDaemon = true) {
            while (isActive) {
                try {
                    val clientSocket = serverSocket.accept()
                    clientSocket.keepAlive = true
                    startClientReader(clientSocket)
                } catch (t: IOException) {
                    if (isActive) {
                        logger.warn("Server Socket exception", t)
                        close()
                    }
                }
            }
        }
    }

    private fun startClientReader(socket: Socket) = thread(name = "Orchestration Client Reader") {
        /* Read Handshake and create the 'client' object */
        val client = try {
            logger.debug("Socket connected: '${socket.remoteSocketAddress}'")
            val input = ObjectInputStream(socket.getInputStream().buffered())
            val output = ObjectOutputStream(socket.getOutputStream().buffered())
            val handshake = input.readObject() as OrchestrationHandshake

            val client = Client(socket, handshake.clientId, handshake.clientRole, input, output)
            logger.debug("Client connected: '$client'")
            lock.withLock { clients.add(client) }

            /* Announce the new client to the whole orchestration */
            sendMessage(ClientConnected(client.id, client.role, handshake.clientPid))
            client
        } catch (t: Throwable) {
            logger.debug("Client cannot be connected: '${socket.remoteSocketAddress}'")
            logger.trace("Client cannot be connected: '${socket.remoteSocketAddress}'", t)
            socket.close()
            return@thread
        }


        /* Read messages  */
        while (isActive) {
            val message = try {
                client.input.readObject()
            } catch (_: IOException) {
                logger.debug("Client disconnected: '$client'")
                lock.withLock { clients.remove(client) }
                client.close()
                break
            }
            if (message !is OrchestrationMessage) {
                logger.debug("Unknown message received '$message'")
                continue
            }

            logger.trace(
                "Received message: ${message.javaClass.simpleName} " +
                    "'$client': '${message.messageId}'"
            )

            /* Broadcasting the message to all clients (including the one it came from) */
            sendMessage(message).get()
        }
    }

    /**
     * Expected to be called from the [orchestrationThread];
     * Will invoke all listeners
     */
    private fun invokeMessageListeners(message: OrchestrationMessage) {
        val listeners = lock.withLock { listeners.toList() }
        listeners.forEach { listener -> listener(message) }
    }

    override fun close() {
        closeGracefully()
    }

    override fun closeGracefully(): Future<Unit> {
        if (isClosed.getAndSet(true)) CompletableFuture.completedFuture(Unit)

        val finished = CompletableFuture<Unit>()
        orchestrationThread.submit {
            lock.withLock {
                try {
                    logger.debug("Closing socket: '${serverSocket.localPort}'")
                    clients.forEach { it.close() }
                    clients.clear()
                    serverSocket.close()
                    closeListeners.forEach { it.invoke() }
                    closeListeners.clear()
                } catch (t: Throwable) {
                    logger.warn("Failed closing server: '${serverSocket.localPort}'", t)
                } finally {
                    /* Send the 'finished' signal after all currently enqueued tasks have finished */
                    orchestrationThread.submit {
                        finished.complete(Unit)
                    }
                }
            }
        }
        return finished
    }

    override fun closeImmediately() {
        if (isClosed.getAndSet(true)) return

        lock.withLock {
            logger.debug("Closing socket (immediately): '${serverSocket.localPort}'")
            clients.forEach { it.close() }
            clients.clear()
            serverSocket.close()
            orchestrationThread.shutdownNow()
        }
    }
}
