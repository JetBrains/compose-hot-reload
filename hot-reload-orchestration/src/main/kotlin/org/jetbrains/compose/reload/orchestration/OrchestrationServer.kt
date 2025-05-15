/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.orchestration

import org.jetbrains.compose.reload.core.Disposable
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.submitSafe
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ClientConnected
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ClientDisconnected
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

internal class OrchestrationServerImpl(
    private val serverSocket: ServerSocket,
    private val logger: Logger
) : OrchestrationServer {
    private val isClosed = AtomicBoolean(false)
    private val isActive get() = !isClosed.get()
    private val lock = ReentrantLock()
    private val listeners = mutableListOf<(OrchestrationMessage) -> Unit>()
    private val closeListeners = mutableListOf<() -> Unit>()
    private val clients = mutableListOf<Client>()

    private class Client(
        val id: UUID,
        val role: OrchestrationClientRole,
        private val socket: Socket,
        val input: ObjectInputStream,
        private val output: ObjectOutputStream,
        private val onClientClosed: ((Client) -> Unit),
    ) : AutoCloseable {

        private val logger = createLogger()

        private val isClosed = AtomicBoolean(false)

        private val isClosedForWrite = AtomicBoolean(false)

        private val writingThread = Executors.newSingleThreadExecutor { runnable ->
            thread(
                name = "Orchestration Client Writer (${socket.remoteSocketAddress})",
                isDaemon = true,
                start = false
            ) {
                try {
                    runnable.run()
                } catch (t: Throwable) {
                    logger.error("'writerThread' exception", t)
                }
            }
        }

        fun write(message: OrchestrationMessage): Future<Unit> {
            return writingThread.submitSafe {
                if (isClosed.get()) error("Client is closed")
                if (isClosedForWrite.get()) error("Client is closed for write")

                try {
                    output.writeObject(message)
                    output.flush()
                } catch (t: Throwable) {
                    isClosedForWrite.set(true)
                    throw t
                }
            }
        }

        override fun close() {
            if (isClosed.getAndSet(true)) return
            isClosedForWrite.set(true)

            runInOrchestrationThreadImmediate {
                try {
                    writingThread.shutdown()
                    if (!writingThread.awaitTermination(1, TimeUnit.SECONDS)) {
                        writingThread.shutdownNow()
                        logger.warn("'writerThread' did not finish gracefully in 1 second '$this'")
                    }

                    onClientClosed(this)
                    socket.close()
                } catch (t: Throwable) {
                    logger.error("Failed closing client: '$this'", t)
                }
            }
        }

        override fun toString(): String {
            return "Client [$role] (${socket.remoteSocketAddress})"
        }
    }

    override val port: Int
        get() = serverSocket.localPort

    fun clients(): Future<List<AutoCloseable>> = orchestrationThread.submitSafe {
        lock.withLock {
            clients.toList()
        }
    }

    override fun invokeWhenMessageReceived(action: (OrchestrationMessage) -> Unit): Disposable {
        val registration = Throwable()

        val safeListener: (OrchestrationMessage) -> Unit = { message ->
            try {
                action(message)
            } catch (t: Throwable) {
                logger.error("Failed invoking orchestration listener", t)
                logger.error("Failing listener was registered at:", registration)
                assert(false) { throw t }
            }
        }
        lock.withLock {
            if (!isActive) return Disposable { }
            listeners.add(safeListener)
        }
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
        /* Send the message to all currently connected clients */
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
                    if (!isActive) {
                        clientSocket.close()
                        break
                    }
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

            val client = lock.withLock {
                if (!isActive) return@thread
                val client = Client(
                    id = handshake.clientId,
                    role = handshake.clientRole,
                    socket = socket,
                    input = input,
                    output = output,
                    onClientClosed = ::onClientClosed
                )
                logger.debug("Client connected: '$client'")
                clients.add(client)
                client
            }

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
        try {
            while (isActive) {
                val message = try {
                    client.input.readObject()
                } catch (_: IOException) {
                    logger.debug("Client disconnected: '$client'")
                    client.close()
                    break
                }

                if (message !is OrchestrationMessage) {
                    logger.debug("Unknown message received '$message'")
                    continue
                }

                /* Broadcasting the message to all clients (including the one it came from) */
                sendMessage(message).get()
            }
        } catch (t: Throwable) {
            logger.error("Failure in client reader", t)
            client.close()
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

    private fun onClientClosed(client: Client) = runInOrchestrationThreadImmediate {
        lock.withLock { clients.remove(client) }
        sendMessage(ClientDisconnected(client.id, client.role))
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
                    clients.toList().forEach { it.close() }
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
        }
    }
}
