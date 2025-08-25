/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.core

import org.jetbrains.compose.reload.DelicateHotReloadApi
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration

/**
 * Represents the 'Future' value of a given 'async' operation.
 * The result of this operation can be obtained by registering a handler using the [invokeOnCompletion] function.
 * The result can also be awaited by blocking the current thread, using the [getBlocking] (which cannot
 * be called from the [reloadMainThread])
 */
@DelicateHotReloadApi
public interface Future<out T> {
    public fun isCompleted(): Boolean
    public fun getOrNull(): Try<T>?
    public suspend fun await(): Try<T>
    public fun awaitWith(continuation: Continuation<T>): Disposable
}

/**
 * Registers a completion handler for this future.
 * If the future is already completed, the result handler will be called with the known value
 * If the future is not yet completed, the result handler will be called once the future completes.
 * The [onResult] will always be called on the [reloadMainThread]
 */
@DelicateHotReloadApi
public fun <T> Future<T>.invokeOnCompletion(onResult: (Try<T>) -> Unit): Disposable {
    return awaitWith(Continuation(EmptyCoroutineContext) { result ->
        reloadMainThread.invoke {
            onResult(result.toTry())
        }
    })
}

@DelicateHotReloadApi
public suspend fun <T> Future<T>.awaitOrThrow(): T = await().getOrThrow()

@DelicateHotReloadApi
public sealed interface CompletableFuture<T> : Future<T> {
    public fun completeWith(result: Try<T>): Boolean
}

@DelicateHotReloadApi
public fun CompletableFuture<Unit>.complete(): Boolean =
    completeWith(Unit.toLeft())

@DelicateHotReloadApi
public fun <T> CompletableFuture<T>.complete(value: T): Boolean =
    completeWith(value.toLeft())

@DelicateHotReloadApi
public fun <T> CompletableFuture<T>.completeWith(result: Try<T>) {
    when (result) {
        is Left<T> -> complete(result.value)
        is Right<Throwable> -> completeExceptionally(result.exception)
    }
}

@DelicateHotReloadApi
public fun <T> CompletableFuture<T>.completeExceptionally(exception: Throwable): Boolean =
    completeWith(exception.toRight())

@DelicateHotReloadApi
public fun <T> Future(): CompletableFuture<T> {
    return FutureImpl()
}

@DelicateHotReloadApi
@JvmName("UnitFuture")
public fun Future(): CompletableFuture<Unit> {
    return Future<Unit>()
}

@DelicateHotReloadApi
@JvmName("FutureFromResult")
public fun <T> Future(result: Result<T>): Future<T> {
    return CompletedFuture(result.toTry())
}

@DelicateHotReloadApi
@JvmName("FutureFromTry")
public fun <T> Future(result: Try<T>): Future<T> {
    return CompletedFuture(result)
}

@DelicateHotReloadApi
@JvmName("FutureFromValue")
public fun <T> Future(result: T): Future<T> {
    return CompletedFuture(result.toLeft())
}

internal fun FailureFuture(result: Throwable): Future<Nothing> {
    return CompletedFuture(result.toRight())
}

internal class FutureImpl<T> : CompletableFuture<T> {

    val state = AtomicReference<State<T>>(State.Waiting<T>(emptyList()))

    override fun isCompleted(): Boolean = state.get() is State.Completed<T>

    sealed class State<out T> {
        data class Waiting<T>(val waiting: List<Continuation<T>>) : State<T>()
        data class Completed<T>(val result: Try<T>) : State<T>()
    }

    override fun getOrNull(): Try<T>? {
        return (state.get() as? State.Completed<T>)?.result
    }

    override fun awaitWith(continuation: Continuation<T>): Disposable {
        state.update { currentState ->
            when (currentState) {
                is State.Completed<T> -> {
                    /* Meanwhile: Already completed, we can continue already */
                    continuation.resumeWith(currentState.result)
                    return Disposable.empty
                }

                /* Try to add the current continuation to the list of waiters */
                is State.Waiting<T> ->
                    currentState.copy(currentState.waiting + continuation)
            }
        }

        return Disposable {
            state.update { currentState ->
                when (currentState) {
                    is State.Completed<*> -> return@Disposable
                    is State.Waiting<T> -> currentState.copy(waiting = currentState.waiting - continuation)
                }
            }
        }
    }

    override suspend fun await(): Try<T> {
        /* Fast path: Already completed */
        (state.get() as? State.Completed<T>)?.result?.let { return it }
        var disposable: Disposable? = null
        return try {
            suspendStoppableCoroutine { continuation ->
                disposable = awaitWith(continuation)
            }
        } finally {
            disposable?.dispose()
        }
    }

    override fun completeWith(result: Try<T>): Boolean {
        while (true) {
            val currentState = state.get()
            when (currentState) {
                is State.Completed<*> -> return false
                is State.Waiting<T> -> {
                    if (!state.compareAndSet(currentState, State.Completed(result))) continue
                    currentState.waiting.forEach { continuation -> continuation.resumeWith(result) }
                    return true
                }
            }
        }
    }

    override fun toString(): String {
        val state = state.get()
        return when (state) {
            is State.Completed<*> -> "Future(${hashCode().toString(32)}): Completed(${state.result})"
            is State.Waiting<*> -> "Future(${hashCode().toString(32)}: Waiting(${state.waiting.size})"
        }
    }
}

private class CompletedFuture<T>(val result: Try<T>) : Future<T> {
    override fun isCompleted(): Boolean {
        return true
    }

    override fun getOrNull(): Try<T> {
        return result
    }

    override suspend fun await(): Try<T> {
        return result
    }

    override fun awaitWith(continuation: Continuation<T>): Disposable {
        continuation.resumeWith(result)
        return Disposable.empty
    }
}

/**
 * Blocks the current thread until the result of the given future is known.
 * @throws IllegalStateException if this function is called from the [reloadMainThread]
 *  - This is done to prevent deadlocking the main thread. An exception is thrown because a failing
 *  result object has different semantics than this precondition.
 */
@DelicateHotReloadApi
public fun <T> Future<T>.getBlocking(): Try<T> {
    if (isReloadMainThread) throw IllegalStateException("Cannot call getBlocking() from the 'reloadMainThread'")
    val javaFuture = java.util.concurrent.CompletableFuture<Try<T>>()
    invokeOnCompletion { javaFuture.complete(it) }
    return javaFuture.get()
}

/**
 * Same as [getBlocking], but with a given [timeout] which will return a failing Result, containing a
 * [TimeoutException] if the timeout is reached.]
 */
@DelicateHotReloadApi
public fun <T> Future<T>.getBlocking(timeout: Duration): Try<T> {
    if (isReloadMainThread) throw IllegalStateException("Cannot call getBlocking() from the 'reloadMainThread'")
    val javaFuture = java.util.concurrent.CompletableFuture<Try<T>>()
    invokeOnCompletion { javaFuture.complete(it) }
    return try {
        javaFuture.get(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
    } catch (t: TimeoutException) {
        return t.toRight()
    }
}

/**
 * Converts a [java.util.concurrent.CompletableFuture] to our async [Future] object.
 */
@DelicateHotReloadApi
public fun <T> java.util.concurrent.CompletableFuture<T>.toFuture(): Future<T> {
    val future = Future<T>()
    whenComplete { value, error ->
        if (error != null) {
            future.completeExceptionally(error)
        } else {
            future.complete(value)
        }
    }
    return future
}
