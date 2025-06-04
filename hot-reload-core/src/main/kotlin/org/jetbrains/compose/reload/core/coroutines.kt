/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.core

import java.util.concurrent.RejectedExecutionException
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.createCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

private val global get() = Task<Unit>()

public fun <T> launchTask(action: suspend Task<T>.() -> T): Task<T> = global.launch { action() }

@InternalHotReloadApi
public suspend fun <T> withThread(
    workerThread: WorkerThread, isImmediate: Boolean = false, action: suspend () -> T
): T {
    val newContext = coroutineContext + WorkerThreadDispatcher(workerThread, isImmediate)
    return suspendStoppableCoroutine { continuation ->
        action.createCoroutine(Continuation(newContext) { result -> continuation.resumeWith(result) })
            .resume(Unit)
    }
}


@InternalHotReloadApi
public suspend fun <T> Future<T>.await(): Try<T> = suspendCoroutine { continuation ->
    invokeOnCompletion { result -> continuation.resume(result) }
}

@InternalHotReloadApi
public suspend fun <T> newTask(action: suspend Task<T>.() -> T): T {
    val task = Task<T>()

    val newContext = coroutineContext + task
    return suspendCoroutine { continuation ->
        action.createCoroutine(task, Continuation(newContext) { result ->
            result.exceptionOrNull()?.let { exception -> task.stop(exception) }
            continuation.resumeWith(result)
        }).resume(Unit)
    }
}

public suspend inline fun <T> suspendStoppableCoroutine(crossinline action: (Continuation<T>) -> Unit): T {
    val task = coroutineContext[Task] ?: error("No 'CoroutineLifecycle' in context")
    var onStop: Disposable? = null
    try {
        return suspendCoroutine { continuation ->
            onStop = task.invokeOnStop { error -> continuation.resumeWithException(StoppedException(error)) }
            action(continuation)
        }
    } finally {
        onStop?.dispose()
    }
}

public suspend fun stopNow(): Nothing = throw StoppedException()

public suspend fun stop(error: Throwable? = null) {
    coroutineContext[Task]?.stop(error) ?: error("No 'CoroutineLifecycle' in context")
}

public suspend fun invokeOnStop(action: (error: Throwable?) -> Unit): Disposable {
    return coroutineContext[Task]?.invokeOnStop(action) ?: error("No 'CoroutineLifecycle' in context")
}

public suspend fun invokeOnFinish(action: (error: Throwable?) -> Unit): Disposable {
    return coroutineContext[Task]?.invokeOnFinish(action) ?: error("No 'CoroutineLifecycle' in context")
}

public suspend fun isActive(): Boolean = coroutineContext[Task]?.isActive ?: false

@OptIn(ExperimentalAtomicApi::class)
public class Task<T> internal constructor(
    private val parent: Task<*>? = null, context: CoroutineContext = EmptyCoroutineContext,
) : CoroutineContext.Element, Future<T> {

    override val key: CoroutineContext.Key<*> = Key

    private val context: CoroutineContext =
        reloadMainDispatcher + (parent?.context ?: EmptyCoroutineContext) + context + this

    private val state = AtomicReference<State>(State.Active(emptyList()))
    private val onFinished = Future<T>()
    private val onStop = Future<Unit>()

    public val isActive: Boolean get() = state.load() is State.Active

    override fun isCompleted(): Boolean {
        return onFinished.isCompleted()
    }

    override fun invokeOnCompletion(onResult: (Try<T>) -> Unit): Disposable {
        return onFinished.invokeOnCompletion(onResult)
    }

    public fun <T> launch(
        context: CoroutineContext = EmptyCoroutineContext,
        action: suspend Task<T>.() -> T
    ): Task<T> {
        /* Enqueue child task */
        val newTask = Task<T>(this, context)
        state.update { currentState ->
            when (currentState) {
                is State.Active -> State.Active(currentState.children + newTask)
                is State.Stopping,
                is State.Finished -> {
                    newTask.onFinished.completeExceptionally(RejectedExecutionException("Task is finished"))
                    return newTask
                }
            }
        }

        action.createCoroutine(newTask, Continuation(newTask.context) { result ->
            /* A failure in the supplied action is supposed to the entire task */
            val failure = result.exceptionOrNull()
            if (failure != null) {
                newTask.stop(if (failure is StoppedException) failure.cause else failure)
            }

            val finish: suspend (Try<T>) -> Try<T> = { result ->
                /* Remove this coroutine */
                state.update { currentState ->
                    when (currentState) {
                        is State.Active -> currentState.copy(children = currentState.children - newTask)
                        is State.Stopping -> currentState.copy(children = currentState.children - newTask)
                        is State.Finished -> error("Illegal state: Task is finished")
                    }
                }

                newTask.finishWith(result)
            }

            finish.createCoroutine(result.toTry(), Continuation(newTask.context) { finishResult ->

            }).resume(Unit)

        }).resume(Unit)

        return newTask
    }

    public fun stop(error: Throwable? = null): Boolean {
        val result =
            error?.toRight() ?: Unit.toLeft()//if (error != null) Result.failure(error) else Result.success(Unit)

        val activeState = state.update { currentState ->
            when (currentState) {
                is State.Active -> State.Stopping(result, currentState.children)
                is State.Stopping -> return false
                is State.Finished -> return false
            }
        }.previous as State.Active

        onStop.completeWith(result)
        activeState.children.forEach { it.stop(error) }
        parent?.stop(error)
        return true
    }

    public fun invokeOnStop(action: (error: Throwable?) -> Unit): Disposable {
        return onStop.invokeOnCompletion { action(it.exceptionOrNull()) }
    }

    public fun invokeOnFinish(action: (error: Throwable?) -> Unit): Disposable {
        return onFinished.invokeOnCompletion { action(it.exceptionOrNull()) }
    }

    public fun launchOnStop(action: suspend (error: Throwable?) -> Unit): Disposable {
        return invokeOnStop {
            launchTask { action(it) }
        }
    }

    public fun launchOnFinish(action: suspend (error: Throwable?) -> Unit): Disposable {
        return invokeOnFinish {
            launchTask { action(it) }
        }
    }

    private suspend fun finishWith(result: Try<T>): Try<T> {
        /* Await all children */
        var finalResult: Try<T> = result
        while (true) {
            val currentState = state.load()
            val children = currentState.children.orEmpty()

            /* All children finished: We can transition to 'Finished' */
            if (children.isEmpty()) {
                if (state.compareAndSet(currentState, State.Finished(finalResult))) {
                    break
                }
            }

            /*
            There are still some children 'in flight':
            Let's await them and conflate the result
             */
            children.forEach { child ->
                val childResult = child.onFinished.await()

                /* Conflate the final result: We'll accept the first failure */
                finalResult = if (finalResult.isSuccess() && childResult.isFailure()) childResult
                else finalResult
            }
        }

        onFinished.completeWith(finalResult)
        return finalResult
    }

    private sealed class State {
        data class Active(override val children: List<Task<*>>) : State()
        data class Stopping(val result: Try<*>, override val children: List<Task<*>>) : State()
        data class Finished(val result: Try<*>) : State() {
            override val children: List<Task<*>>? = null
        }

        abstract val children: List<Task<*>>?
    }

    public companion object Key : CoroutineContext.Key<Task<*>>
}

public class StoppedException(override val cause: Throwable? = null) : Exception()
