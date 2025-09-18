/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.core

import org.jetbrains.compose.reload.InternalHotReloadApi
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean

@InternalHotReloadApi
public class WorkerThread(
    name: String, isDaemon: Boolean = true,
) : Thread(name), AutoCloseable {
    private val queue = LinkedBlockingQueue<Work<*>>()
    private val idleQueue = LinkedBlockingQueue<Work<*>>()

    private val isShutdown = AtomicBoolean(false)
    private val isClosed = Future<Unit>()

    override fun run() {
        fun <T> Work<T>.execute() {
            future.completeWith(Try { action() })
        }

        fun cleanupIdleQueue() {
            while (idleQueue.isNotEmpty()) {
                val idleElement = idleQueue.poll()
                idleElement?.execute()
            }
        }

        try {
            while (true) {
                if (queue.isEmpty() && idleQueue.isNotEmpty()) {
                    val idleElement = idleQueue.poll()
                    idleElement?.execute()
                    continue
                }

                val element = queue.take()
                if (element is Work.Shutdown) {
                    cleanupIdleQueue()
                    break
                }
                element.execute()
            }
        } finally {
            isClosed.complete(Unit)
        }
    }

    public fun shutdown(): Future<Unit> {
        if (isShutdown.compareAndSet(false, true)) {
            queue.add(Work.Shutdown)
        }
        return isClosed
    }

    override fun close() {
        shutdown()
    }

    public fun <T> invokeWhenIdle(action: () -> T): Future<T> {
        val future = enqueue(idleQueue, action)
        queue.add(Work.Empty)
        return future
    }

    /**
     * Invokes the given action on the worker thread.
     * If the thread is already shut-down, or shutting down, the [FailureFuture] might contain a [RejectedExecutionException].
     */
    public fun <T> invoke(action: () -> T): Future<T> {
        return enqueue(queue, action)
    }

    /**
     * Similar to the [invoke] method, but immediately calls the [action] if this method
     * is already invoked on the correct thread.
     */
    public fun <T> invokeImmediate(action: () -> T): Future<T> {
        return if (currentThread() == this) Future(Try { action() })
        else invoke(action)
    }

    /**
     * Similar to the [invokeImmediate] method, but blocks the current thread until the [action] is completed if
     * the current thread was not the worker thread.
     *
     * If the current thread is the worker thread, the [action] is executed immediately.
     */
    public fun <T> invokeImmediateBlocking(action: () -> T): T {
        return if (currentThread() == this) action()
        else invoke(action).getBlocking().getOrThrow()
    }

    private fun <T> enqueue(queue: LinkedBlockingQueue<Work<*>>, action: () -> T): Future<T> {
        val work = Work.Action(action)
        if (isShutdown.get()) {
            return FailureFuture(RejectedExecutionException("WorkerThread '$name' is shutting down"))
        }

        queue.add(work)
        return work.future
    }

    private sealed class Work<T>(val action: () -> T) {
        val future: CompletableFuture<T> = Future<T>()

        object Empty : Work<Unit>({})
        object Shutdown : Work<Unit>({})
        class Action<T>(action: () -> T) : Work<T>(action)
    }

    init {
        if (isDaemon) this.isDaemon = true
        start()
    }
}
