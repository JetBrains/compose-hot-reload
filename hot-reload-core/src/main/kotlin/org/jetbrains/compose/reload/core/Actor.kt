/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */


package org.jetbrains.compose.reload.core

import java.util.concurrent.atomic.AtomicReference

/**
 * Primitive for two coroutines (with separate lifecycles (tasks)) communicating.
 * The inputs sent using the [invoke] method will be processed by the actor, connected using the [process] method.
 */
public interface Actor<In, Out> {
    public suspend operator fun invoke(input: In): Out
    public suspend fun process(action: suspend (In) -> Out)

    public fun close(error: Throwable? = null): Boolean
    public fun isClosed(): Boolean
}

public fun <In, Out> Actor(): Actor<In, Out> = ActorImpl()


public class ActorClosedException(override val cause: Throwable? = null) : Exception()

private class ActorImpl<In, Out> : Actor<In, Out> {

    private val queue = Queue<Element<In, Out>>()
    private val state = AtomicReference<State>(State.Idle)

    sealed class Element<out Int, out Out>
    class CompletableInput<In, Out>(val input: In, val future: CompletableFuture<Out>) : Element<In, Out>()
    object QueueClosed : Element<Nothing, Nothing>()


    sealed class State {
        object Idle : State()
        data class Processing(val future: Future<Unit>) : State()
        data class Closed(val exception: Throwable) : State()
    }

    override suspend fun invoke(input: In): Out {
        val currentState = state.get()
        if (currentState is State.Closed) {
            throw currentState.exception
        }

        val future = Future<Out>()
        queue.send(CompletableInput(input, future))
        return future.await().getOrThrow()
    }

    override fun close(error: Throwable?): Boolean {
        val exception = error ?: ActorClosedException()

        val previousState = state.update {
            if (it is State.Closed) return false
            State.Closed(exception)
        }.previous


        launchTask("ActorImpl.close") {
            queue.send(QueueClosed)

            if (previousState is State.Processing) {
                previousState.future.await()
            }

            while (isActive()) {
                when (val element = queue.receive()) {
                    is QueueClosed -> continue
                    is CompletableInput<*, *> -> element.future.completeExceptionally(exception)
                }
            }
        }

        return true
    }

    override fun isClosed(): Boolean {
        return state.get() is State.Closed
    }

    override suspend fun process(action: suspend (In) -> Out) {
        val thisProcess = Future<Unit>()

        state.update { currentState ->
            when (currentState) {
                is State.Idle -> State.Processing(thisProcess)
                is State.Processing -> error("There is already a running process for this actor")
                is State.Closed -> error("This actor is closed")
            }
        }

        fun complete(error: Throwable?) {
            if (error != null) thisProcess.completeExceptionally(error)
            else thisProcess.complete(Unit)
            close()
        }

        /* Handle the case where the current coroutines task finishes before calling any 'finally' block */
        val onFinish = invokeOnStop { error ->
            complete(error)
        }

        try {
            while (isActive()) {
                val element = when (val element = queue.receive()) {
                    is QueueClosed -> break
                    is CompletableInput<*, *> -> element as CompletableInput<In, Out>
                }

                /* Handle the case where the current coroutine task finished before calling the 'finally' block */
                val elementOnFinish = invokeOnFinish { error ->
                    element.future.completeExceptionally(ActorClosedException(error.exceptionOrNull()))
                }
                try {
                    val result = action(element.input)
                    element.future.complete(result)
                } catch (t: Throwable) {
                    element.future.completeExceptionally(t)
                    throw t
                } finally {
                    elementOnFinish.dispose()
                }
            }
        } catch (t: Throwable) {
            complete(t)
            throw t
        } finally {
            onFinish.dispose()
        }
        complete(null)
    }
}
