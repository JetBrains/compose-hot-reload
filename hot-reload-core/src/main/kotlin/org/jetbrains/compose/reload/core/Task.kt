/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.core

import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.createCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

private val logger = createLogger()

public interface Task<out T> : Future<T>, CoroutineContext.Element {
    public val name: String?
    public val value: Future<T>
    public val onStop: Future<Nothing>
    public fun stop(exception: Throwable? = null): Boolean

    public fun <T> subtask(
        name: String? = null, context: CoroutineContext = EmptyCoroutineContext, body: suspend Task<T>.() -> T
    ): Task<T>

    override val key: CoroutineContext.Key<*> get() = Key

    public companion object Key : CoroutineContext.Key<Task<*>>
}

/**
 * Note: This method will yield the current thread if possible.
 * This allows writing loops using
 * ```kotlin
 * while(isActive()) {
 *     // blocking code
 * }
 * ```
 * @return true if the current task is still executing (no value produced, not stopped)
 */
public suspend fun Task<*>.isActive(): Boolean {
    val currentContext = coroutineContext
    return suspendCoroutine { continuation ->
        suspend {
            !value.isCompleted() && !isStopped()
        }.createCoroutine(Continuation(currentContext) { result ->
            continuation.resumeWith(result)
        }).resume(Unit)
    }
}


public fun Task<*>.isStopped(): Boolean = onStop.isCompleted()

public fun Task<*>.invokeOnStop(action: (error: Throwable) -> Unit): Disposable {
    return onStop.invokeOnCompletion { result ->
        action(result.rightOr { it.value })
    }
}

public fun Task<*>.launchOnStop(name: String, body: suspend Task<*>.(error: Throwable) -> Unit): Disposable =
    invokeOnStop { error ->
        launchTask(name) {
            body(error)
        }
    }

public fun Task<*>.invokeOnError(action: (error: Throwable) -> Unit): Disposable {
    return value.invokeOnCompletion { result ->
        if (result.isFailure()) {
            action(result.exception)
        }
    }
}

public fun Task<*>.launchOnError(name: String, body: suspend Task<*>.(error: Throwable) -> Unit): Disposable =
    invokeOnError { error ->
        launchTask(name) {
            body(error)
        }
    }

public fun <T> Task<T>.invokeOnFinish(action: (result: Try<T>) -> Unit): Disposable {
    return value.invokeOnCompletion { result -> action(result) }
}

public fun <T> Task<T>.launchOnFinish(name: String, action: suspend Task<*>.(result: Try<T>) -> Unit): Disposable =
    invokeOnFinish { result ->
        launchTask(name) {
            action(result)
        }
    }

public fun <T> launchTask(body: suspend Task<T>.() -> T): Task<T> {
    return launchTask(name = null, body = body)
}

public fun <T> launchTask(
    name: String? = null, context: CoroutineContext = EmptyCoroutineContext, body: suspend Task<T>.() -> T
): Task<T> {
    val task = TaskImpl(name, context.withAsyncTraces("launchTask($name)"), body)
    task.start()
    return task
}

private class TaskImpl<T>(
    override val name: String?,
    private val coroutineContext: CoroutineContext,
    private val action: suspend Task<T>.() -> T
) : Task<T> {
    private val state = AtomicReference<State<T>>(State.Idle)
    override val value = Future<T>()
    override val onStop = Future<Nothing>()

    fun reject() {
        state.update { currentState ->
            when (currentState) {
                is State.Idle -> State.Finished(RejectedExecutionException().toRight())
                else -> error("Unexpected state: $currentState")
            }
        }
    }

    fun start() {
        state.update { currentState ->
            when (currentState) {
                is State.Idle -> State.Running(emptyList())
                else -> error("Unexpected state: $currentState")
            }
        }

        suspend {
            /* Starting the task */
            val result = Try { action() }

            /* A failure will cause the entire task graph to stop */
            if (result.isFailure()) {
                val asyncTraces = coroutineContext[AsyncTraces]
                if (asyncTraces != null) {
                    result.exception.addSuppressed(AsyncTracesThrowable(asyncTraces))
                }

                stop(result.exception)
            }

            /* Finishing: Waiting for all children */
            while (true) {
                val currentState: State<T> = state.get()
                val children = when (currentState) {
                    is State.Running -> currentState.children
                    is State.Stopping -> currentState.children
                    is State.Finished<T>, is State.Idle -> error("Unexpected state: $state")
                }

                /* There are some children: Let's await their result */
                if (children.isNotEmpty()) {
                    children.forEach { child ->
                        child.value.await()
                    }

                    /*
                     Helping: We know that the children are finished, therefore, we can update/maintain the
                     state right away
                     */
                    state.update { currentState ->
                        when (currentState) {
                            is State.Running -> currentState.copy(children = currentState.children - children)
                            is State.Stopping -> currentState.copy(children = currentState.children - children)
                            State.Idle, is State.Finished<*> -> currentState
                        }
                    }

                    continue
                }

                /* All children finished, we can try transitioning into the 'Finished' state */
                val finished: State.Finished<T> = when (currentState) {
                    is State.Running -> State.Finished(result)
                    is State.Stopping -> State.Finished(currentState.exception.toRight())
                    else -> error("Unexpected state: $state")
                }

                if (state.compareAndSet(currentState, finished)) {
                    value.completeWith(finished.result)
                    break
                }
            }
        }.createCoroutine(Continuation(reloadMainDispatcher + coroutineContext + this) { result ->
            if (result.isFailure) {
                logger.error("Exception in task handling", result.exceptionOrNull())
            }
        }).resume(Unit)
    }


    override fun <T> subtask(
        name: String?, context: CoroutineContext, body: suspend Task<T>.() -> T
    ): Task<T> {

        val subtask = TaskImpl(name, context.withAsyncTraces("subtask($name)"), body)

        state.update { currentState ->
            when (currentState) {
                is State.Idle -> error("Cannot call subtask() from the 'Idle' state")
                is State.Running -> currentState.copy(children = currentState.children + subtask)
                is State.Stopping -> currentState.copy(children = currentState.children + subtask)
                is State.Finished -> {
                    subtask.reject()
                    return subtask
                }
            }
        }

        subtask.invokeOnStop { exception ->
            stop(exception)
        }

        subtask.invokeOnFinish {
            state.update { currentState ->
                when (currentState) {
                    is State.Running -> currentState.copy(children = currentState.children - subtask)
                    is State.Stopping -> currentState.copy(children = currentState.children - subtask)
                    is State.Finished -> currentState
                    is State.Idle -> currentState
                }
            }
        }

        subtask.start()
        return subtask
    }

    override fun stop(exception: Throwable?): Boolean {
        val exception by lazy { exception ?: StoppedException() }

        val stopping = state.update { currentState ->
            when (currentState) {
                is State.Idle -> State.Finished<T>((exception).toRight())
                is State.Running -> State.Stopping(children = currentState.children, exception = exception)
                is State.Stopping -> return false
                is State.Finished<*> -> return false
            }
        }.updated as? State.Stopping

        stopping?.children.orEmpty().forEach { child ->
            child.stop(exception)
        }

        onStop.completeExceptionally(exception)
        return true
    }

    override fun isCompleted(): Boolean = value.isCompleted()
    override fun getOrNull(): Try<T>? = value.getOrNull()
    override suspend fun await(): Try<T> = value.await()
    override fun awaitWith(continuation: Continuation<T>): Disposable = value.awaitWith(continuation)

    private sealed class State<out T> {
        data object Idle : State<Nothing>()
        data class Running(val children: List<Task<*>>) : State<Nothing>()
        data class Stopping(val children: List<Task<*>>, val exception: Throwable) : State<Nothing>()
        data class Finished<T>(val result: Try<T>) : State<T>()
    }
}
