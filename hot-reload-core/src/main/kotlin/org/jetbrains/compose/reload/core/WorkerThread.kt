/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.core

import org.jetbrains.compose.reload.InternalHotReloadApi
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicInteger

@InternalHotReloadApi
public class WorkerThread(
    name: String, isDaemon: Boolean = true,
) : Thread(name), AutoCloseable {
    private val queue = LinkedBlockingQueue<Work<*>>()
    private val idleQueue = LinkedBlockingQueue<Work<*>>()

    /**
     * [Int.MIN_VALUE]: The worker thread is closed
     * Int.MIN_VALUE + n: The worker thread is shutting down, but there are still [n] pending dispatches
     * 0..Int.MAX_VALUE: The worker thread is running and accepting dispatches
     */
    private val pendingDispatches = AtomicInteger(0)
    private val isClosed = Future<Unit>()

    override fun run() {
        fun <T> Work<T>.execute() {
            future.completeWith(Try { action() })
        }

        try {
            while (pendingDispatches.get() != Int.MIN_VALUE || queue.isNotEmpty() || idleQueue.isNotEmpty()) run outer@{
                if (queue.isEmpty() && idleQueue.isNotEmpty()) {
                    val idleElement = idleQueue.poll()
                    idleElement?.execute()
                    return@outer
                }

                val element = queue.take()
                element.execute()
            }
        } finally {
            isClosed.complete(Unit)
        }
    }

    public fun shutdown(): Future<Unit> {
        /* Try closing the worker thread by setting 'pendingDispatches' to 'Int.MIN_VALUE' */
        while (true) {
            val currentPendingDispatches = pendingDispatches.get()
            if (currentPendingDispatches < 0) return isClosed
            if (pendingDispatches.compareAndSet(currentPendingDispatches, Int.MIN_VALUE + currentPendingDispatches)) {
                /* Send an empty task to awaken the worker thread */
                queue.add(Work.empty)
                return isClosed
            }
        }
    }

    override fun close() {
        shutdown()
    }

    public fun <T> invokeWhenIdle(action: () -> T): Future<T> {
        val future = enqueue(idleQueue, action)
        queue.add(Work.empty)
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

    private fun <T> enqueue(queue: LinkedBlockingQueue<Work<*>>, action: () -> T): Future<T> {
        val future = Future<T>()

        /* Fast path: The thread is already closed for further dispatches */
        if (pendingDispatches.get() < 0) {
            return FailureFuture(RejectedExecutionException("WorkerThread '$name' is shutting down"))
        }

        val work = Work(future, action)
        val previousPendingDispatches = pendingDispatches.andIncrement
        try {
            if (previousPendingDispatches < 0) {
                return FailureFuture(RejectedExecutionException("WorkerThread '$name' is shutting down"))
            }

            queue.add(work)
        } finally {
            pendingDispatches.andDecrement
        }
        return future
    }

    private class Work<T>(val future: CompletableFuture<T>, val action: () -> T) {
        companion object {
            val empty get() = Work(Future(), {})
        }
    }

    init {
        if (isDaemon) this.isDaemon = true
        start()
    }
}
