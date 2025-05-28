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
import kotlin.coroutines.suspendCoroutine

private val global get() = Task()

public fun <T> globalLaunch(action: suspend Task.() -> T): Future<T> = global.launch { action() }

@InternalHotReloadApi
public suspend fun <T> withThread(
    workerThread: WorkerThread, isImmediate: Boolean = false, action: suspend () -> T
): T {
    val newContext = coroutineContext + WorkerThreadDispatcher(workerThread, isImmediate)
    return suspendCoroutine { continuation ->
        action.createCoroutine(Continuation(newContext) { result -> continuation.resumeWith(result) })
            .resume(Unit)
    }
}

@InternalHotReloadApi
public suspend fun <T> Future<T>.await(): T = suspendCoroutine { continuation ->
    invokeOnCompletion { result -> continuation.resumeWith(result) }
}

@InternalHotReloadApi
public suspend fun <T> newTask(action: suspend Task.() -> T): T {
    val task = Task()

    val newContext = coroutineContext + task
    return suspendCoroutine { continuation ->
        action.createCoroutine(task, Continuation(newContext) { result ->
            result.exceptionOrNull()?.let { exception -> task.stop(exception) }
            continuation.resumeWith(result)
        }).resume(Unit)
    }
}

public suspend fun stop(error: Throwable? = null) {
    coroutineContext[Task]?.stop(error) ?: error("No 'CoroutineLifecycle' in context")
}

public suspend fun invokeOnStop(action: (error: Throwable?) -> Unit) {
    coroutineContext[Task]?.invokeOnStop(action) ?: error("No 'CoroutineLifecycle' in context")
}


@OptIn(ExperimentalAtomicApi::class)
public class Task internal constructor(
    context: CoroutineContext = EmptyCoroutineContext,
) : CoroutineContext.Element {

    override val key: CoroutineContext.Key<*> = Key

    private val context = reloadMainDispatcher + context + this
    private val state = AtomicReference<State>(State.Active(emptyList()))
    private val onFinished = Future<Result<*>>()
    private val onStop = Future<Unit>()

    public fun <T> launch(
        context: CoroutineContext = EmptyCoroutineContext,
        action: suspend Task.() -> T
    ): Future<T> {
        /* Enqueue child task */
        val newTask = Task(this.context + context)
        state.update { currentState ->
            when (currentState) {
                is State.Active -> State.Active(currentState.children + newTask)
                is State.Stopping,
                is State.Finished -> return FailureFuture(RejectedExecutionException("Task is not active"))
            }
        }

        val future = Future<T>()

        action.createCoroutine(newTask, Continuation(newTask.context) { result ->
            /* A failure in the supplied action is supposed to the entire task */
            if (result.isFailure) {
                stop(result.exceptionOrNull())
            }

            /* Remove this coroutine */
            state.update { currentState ->
                when (currentState) {
                    is State.Active -> currentState.copy(children = currentState.children - newTask)
                    is State.Stopping -> currentState.copy(children = currentState.children - newTask)
                    is State.Finished -> error("Illegal state: Task is finished")
                }
            }

            val finish: suspend (Result<T>) -> Result<T> = { result ->
                newTask.finishWith(result)
            }

            finish.createCoroutine(result, Continuation(newTask.context) { finishResult ->
                future.completeWith(finishResult.getOrThrow())
            }).resume(Unit)

        }).resume(Unit)

        return future
    }

    public fun stop(error: Throwable? = null): Boolean {
        val result = if (error != null) Result.failure(error) else Result.success(Unit)

        val activeState = state.update { currentState ->
            when (currentState) {
                is State.Active -> State.Stopping(result, currentState.children)
                is State.Stopping -> return false
                is State.Finished -> return false
            }
        }.previous as State.Active

        onStop.completeWith(result)
        activeState.children.forEach { it.stop(error) }
        return true
    }

    public fun invokeOnStop(action: (error: Throwable?) -> Unit) {
        onStop.invokeOnCompletion { action(it.exceptionOrNull()) }
    }

    private suspend fun <T> finishWith(result: Result<T>): Result<T> {
        /* Await all children */
        var finalResult: Result<T> = result
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
                finalResult = if (finalResult.isSuccess && childResult.isFailure)
                    Result.failure(childResult.exceptionOrNull()!!)
                else finalResult
            }
        }

        onFinished.complete(finalResult)
        return finalResult
    }

    private sealed class State {
        data class Active(override val children: List<Task>) : State()
        data class Stopping(val result: Result<*>, override val children: List<Task>) : State()
        data class Finished(val result: Result<*>) : State() {
            override val children: List<Task>? = null
        }

        abstract val children: List<Task>?
    }

    public companion object Key : CoroutineContext.Key<Task>
}
