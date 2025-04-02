/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.orchestration

import org.jetbrains.compose.reload.core.Disposable
import org.jetbrains.compose.reload.core.HotReloadEnvironment
import org.jetbrains.compose.reload.core.submitSafe
import org.slf4j.LoggerFactory
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
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


public interface OrchestrationClient : OrchestrationHandle {
    public val clientId: UUID
}

public fun OrchestrationClient(role: OrchestrationClientRole): OrchestrationClient? {
    val port = HotReloadEnvironment.orchestrationPort
    if (port == null) return null

    return connectOrchestrationClient(role, port = port)
}

public fun connectOrchestrationClient(role: OrchestrationClientRole, port: Int): OrchestrationClient {
    val socket = Socket("127.0.0.1", port)

    socket.keepAlive = true
    val client = OrchestrationClientImpl(role, socket, port)
    client.start()
    return client
}

private class OrchestrationClientImpl(
    private val role: OrchestrationClientRole,
    private val socket: Socket,
    override val port: Int,
) : OrchestrationClient {

    override val clientId: UUID = UUID.randomUUID()
    private val logger = LoggerFactory.getLogger("OrchestrationClient(${socket.localPort})")
    private val lock = ReentrantLock()
    private val isClosed = AtomicBoolean(false)
    private val isActive get() = !isClosed.get()
    private val listeners = mutableListOf<(OrchestrationMessage) -> Unit>()
    private val closeListeners = mutableListOf<() -> Unit>()

    private val writer = object : AutoCloseable {
        private val output = ObjectOutputStream(socket.getOutputStream().buffered())

        private val thread = Executors.newSingleThreadExecutor { runnable ->
            thread(name = "Orchestration Client Writer", isDaemon = true, start = false) {
                runnable.run()
            }
        }

        fun sendMessage(any: Any): Future<Unit> = thread.submitSafe {
            try {
                output.writeObject(any)
                output.flush()
            } catch (_: Throwable) {
                logger.debug("writer: Closing client")
                close()
            }
        }

        override fun close() {
            /* Shutdown writer thread and await all messages to be written */
            thread.shutdown()
            if (!thread.awaitTermination(1, TimeUnit.SECONDS)) {
                logger.warn("'writer' did not finish gracefully in 1 second")
            }

            output.close()
        }
    }


    override fun invokeWhenMessageReceived(action: (OrchestrationMessage) -> Unit): Disposable {
        lock.withLock { listeners.add(action) }
        return Disposable {
            lock.withLock { listeners.remove(action) }
        }
    }

    override fun invokeWhenClosed(action: () -> Unit) {
        lock.withLock {
            if (isClosed.get()) action()
            else closeListeners.add(action)
        }
    }

    override fun sendMessage(message: OrchestrationMessage): Future<Unit> {
        return writer.sendMessage(message)
    }

    fun start() {
        writer.sendMessage(OrchestrationHandshake(clientId, role, ProcessHandle.current().pid()))

        thread(name = "Orchestration Client Reader") {
            logger.debug("connected")

            try {
                val input = ObjectInputStream(socket.getInputStream().buffered())

                while (isActive) {
                    val message = input.readObject()
                    if (message !is OrchestrationMessage) {
                        logger.debug("Unknown message received '$message'")
                        continue
                    }

                    /* Notify orchestration thread about the message */
                    orchestrationThread.submit {
                        val listeners = lock.withLock { listeners.toList() }
                        listeners.forEach { listener -> listener(message) }
                    }.get()
                }
            } catch (t: Throwable) {
                logger.debug("reader: closing client")
                logger.trace("reader: closed with traces", t)
                close()
            }
        }
    }

    override fun close() {
        closeGracefully()
    }

    override fun closeGracefully(): Future<Unit> {
        if (isClosed.getAndSet(true)) return CompletableFuture.completedFuture(Unit)


        /* Close socket and invoke all close listeners */
        val finished = CompletableFuture<Unit>()
        orchestrationThread.submit {
            try {
                logger.debug("Closing write")
                writer.close()

                logger.debug("Closing socket: '${socket.port}' ('${socket.localPort}')")
                socket.close()

                val closeListeners = lock.withLock {
                    try {
                        closeListeners.toList()
                    } finally {
                        closeListeners.clear()
                    }
                }

                closeListeners.forEach { listener ->
                    try {
                        listener.invoke()
                    } catch (t: Throwable) {
                        logger.error("Failed invoking close listener", t)
                    }
                }
            } finally {
                /* Send 'finished' signal when all currently enqueued tasks were completed */
                orchestrationThread.submit {
                    finished.complete(Unit)
                }
            }
        }

        return finished
    }

    override fun closeImmediately() {
        if (isClosed.getAndSet(true)) return
        logger.debug("Closing socket (immediately): '${socket.port}' ('${socket.localPort}')")
        socket.close()
    }
}
