/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.core

import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.RejectedExecutionException
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
public class WorkerThread(
    name: String, isDaemon: Boolean = true,
) : Thread(name), AutoCloseable {

    private val logger = createLogger()

    private val queue = LinkedBlockingQueue<QueueElement>()
    private var state = AtomicReference<State>(State.Running.free)

    override fun run() {
        while (true) {
            val element = queue.take()
            when (element) {
                End -> break
                is Work<*> -> element.execute()
            }
        }

        val previousState = state.exchange(State.Stopped)
        if (previousState !is State.Stopping) {
            logger.error("WorkerThread '$name' finished from '$previousState' (expected ${State.Stopping::class})")
            return
        }

        previousState.future.completeWith(Result.success(Unit))
    }

    public fun shutdown(): Future<Unit> {
        val future = Future<Unit>()
        while (true) {
            val currentState = state.load()
            when (currentState) {
                is State.Stopping -> return currentState.future
                is State.Stopped -> return Future(Result.success(Unit))
                is State.Running -> {
                    /* Can't do a state transition: A dispatch is currently happening */
                    if (currentState.dispatching != null) continue

                    /* We safely transitioned to 'Stopping' */
                    if (state.compareAndSet(currentState, State.Stopping(future))) break
                }
            }
        }

        queue.add(End)
        return future
    }

    override fun close() {
        shutdown()
    }

    /**
     * Invokes the given action on the worker thread.
     * If the thread is already shut-down, or shutting down, the [Future] might contain a [RejectedExecutionException].
     */
    public fun <T> invoke(action: () -> T): Future<T> {
        val future = Future<T>()
        val work = Work(future, action)
        val dispatchState = State.Running(work)
        while (true) {
            val currentState = state.load()

            /* Fast track: The thread is not running anymore */
            if (currentState !is State.Running) return future.apply {
                completeWith(Result.failure(RejectedExecutionException("'$name' is '$state")))
            }

            /* Loop further: Currently someone else is dispatching */
            if (currentState.dispatching != null) continue

            /* If we were able to reserve the dispatch state, break the loop */
            if (state.compareAndSet(currentState, dispatchState)) break
        }

        queue.add(work)
        state.compareAndSet(dispatchState, State.Running.free)
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

    private sealed class State {
        class Running(val dispatching: Work<*>? = null) : State() {
            companion object {
                val free = Running()
            }
        }

        class Stopping(val future: CompletableFuture<Unit>) : State()
        object Stopped : State()
    }

    private sealed class QueueElement

    private class Work<T>(private val future: CompletableFuture<T>, private val action: () -> T) : QueueElement() {
        fun execute() {
            val result = runCatching { action() }
            future.completeWith(result)
        }
    }

    private data object End : QueueElement()

    init {
        if (isDaemon) this.isDaemon = true
        start()
    }
}
