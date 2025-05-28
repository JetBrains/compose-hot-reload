/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.core

import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.RejectedExecutionException
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.fetchAndDecrement
import kotlin.concurrent.atomics.fetchAndIncrement

@OptIn(ExperimentalAtomicApi::class)
public class WorkerThread(
    name: String, isDaemon: Boolean = true,
) : Thread(name), AutoCloseable {
    private val queue = LinkedBlockingQueue<Work<*>>()

    /**
     * [Int.MIN_VALUE]: The worker thread is closed
     * Int.MIN_VALUE + n: The worker thread is shutting down, but there are still [n] pending dispatches
     * 0..Int.MAX_VALUE: The worker thread is running and accepting dispatches
     */
    private val pendingDispatches = AtomicInt(0)
    private val isClosed = Future<Unit>()

    override fun run() {
        try {
            while (pendingDispatches.load() != Int.MIN_VALUE) {
                val element = queue.take()
                element.execute()
            }
        } finally {
            isClosed.completeWith(Result.success(Unit))
        }
    }

    public fun shutdown(): Future<Unit> {
        /* Try closing the worker thread by setting 'pendingDispatches' to 'Int.MIN_VALUE' */
        while (true) {
            val currentPendingDispatches = pendingDispatches.load()
            if (currentPendingDispatches < 0) return isClosed
            if (pendingDispatches.compareAndSet(currentPendingDispatches, Int.MIN_VALUE + currentPendingDispatches)) {
                /* Send an empty task to awaken the worker thread */
                queue.add(Work(Future()) {})
                return isClosed
            }
        }
    }

    override fun close() {
        shutdown()
    }

    /**
     * Invokes the given action on the worker thread.
     * If the thread is already shut-down, or shutting down, the [FailureFuture] might contain a [RejectedExecutionException].
     */
    public fun <T> invoke(action: () -> T): Future<T> {
        val future = Future<T>()

        /* Fast path: The thread is already closed for further dispatches */
        if (pendingDispatches.load() < 0) {
            return FailureFuture(RejectedExecutionException("WorkerThread '$name' is shutting down"))
        }

        val work = Work(future, action)
        val previousPendingDispatches = pendingDispatches.fetchAndIncrement()
        try {
            if (previousPendingDispatches < 0) {
                return FailureFuture(RejectedExecutionException("WorkerThread '$name' is shutting down"))
            }

            queue.add(work)
        } finally {
            pendingDispatches.fetchAndDecrement()
        }
        return future
    }

    /**
     * Similar to the [invoke] method, but immediately calls the [action] if this method
     * is already invoked on the correct thread.
     */
    public fun <T> invokeImmediate(action: () -> T): Future<T> {
        return if (currentThread() == this) Future(runCatching { action() })
        else invoke(action)
    }

    private class Work<T>(private val future: CompletableFuture<T>, private val action: () -> T) {
        fun execute() {
            val result = runCatching { action() }
            future.completeWith(result)
        }
    }

    init {
        if (isDaemon) this.isDaemon = true
        start()
    }
}
