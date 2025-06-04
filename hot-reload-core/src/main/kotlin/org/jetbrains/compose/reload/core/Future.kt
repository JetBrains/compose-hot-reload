/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.core

import java.util.concurrent.TimeoutException
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.withLock
import kotlin.time.Duration

/**
 * Represents the 'Future' value of a given 'async' operation.
 * The result of this operation can be obtained by registering a handler using the [invokeOnCompletion] function.
 * The result can also be awaited by blocking the current thread, using the [getBlocking] (which cannot
 * be called from the [reloadMainThread])
 */
public interface Future<out T> {

    public fun isCompleted(): Boolean

    /**
     * Registers a completion handler for this future.
     * If the future is already completed, the result handler will be called with the known value
     * If the future is not yet completed, the result handler will be called once the future completes.
     * The [onResult] will always be called on the [reloadMainThread]
     */
    public fun invokeOnCompletion(onResult: (Try<T>) -> Unit): Disposable
}

public interface CompletableFuture<T> : Future<T> {
    public fun completeWith(result: Try<T>): Boolean
}

public fun <T> CompletableFuture<T>.complete(value: T) =
    completeWith(value.toLeft())

public fun <T> CompletableFuture<T>.completeWith(result: Try<T>) {
    when (result) {
        is Left<T> -> complete(result.value)
        is Right<Throwable> -> completeExceptionally(result.exception)
    }
}

public fun <T> CompletableFuture<T>.completeExceptionally(exception: Throwable) =
    completeWith(exception.toRight())

public fun <T> Future(): CompletableFuture<T> {
    return FutureImpl()
}

internal fun <T> Future(result: Result<T>): Future<T> {
    return CompletedFuture(result.toTry())
}

internal fun <T> Future(result: Try<T>): Future<T> {
    return CompletedFuture(result)
}

internal fun <T> SuccessFuture(result: T): Future<T> {
    return CompletedFuture(result.toLeft())
}

internal fun FailureFuture(result: Throwable): Future<Nothing> {
    return CompletedFuture(result.toRight())
}

private class FutureImpl<T> : CompletableFuture<T> {
    private val lock = ReentrantLock()

    private val listeners = mutableListOf<(Try<T>) -> Unit>()

    @Volatile
    private var result: Try<T>? = null

    override fun isCompleted(): Boolean = lock.withLock {
        result != null
    }

    override fun invokeOnCompletion(onResult: (Try<T>) -> Unit): Disposable = lock.withLock {
        val result = result

        /* Fast path: Result is already known */
        if (result != null) {
            reloadMainThread.invoke { onResult(result) }
            Disposable {}
        }

        /* Slow path: Create a future and wait for completion */
        val listener: (Try<T>) -> Unit = { result: Try<T> ->
            reloadMainThread.invokeImmediate { onResult(result) }
        }
        listeners.add(listener)

        Disposable {
            lock.withLock {
                listeners.remove(listener)
            }
        }
    }


    override fun completeWith(result: Try<T>): Boolean {
        val listeners = lock.withLock {
            if (this.result != null) return false
            this.result = result
            listeners.toTypedArray().apply {
                listeners.clear()
            }
        }

        listeners.forEach { listener ->
            listener(result)
        }
        return true
    }
}

private class CompletedFuture<T>(val result: Try<T>) : Future<T> {
    override fun isCompleted(): Boolean {
        return true
    }

    override fun invokeOnCompletion(onResult: (Try<T>) -> Unit): Disposable {
        reloadMainThread.invoke { onResult(result) }
        return Disposable {}
    }
}

/**
 * Blocks the current thread until the result of the given future is known.
 * @throws IllegalStateException if this function is called from the [reloadMainThread]
 *  - This is done to prevent deadlocking the main thread. An exception is thrown because a failing
 *  result object has different semantics than this precondition.
 */
@OptIn(ExperimentalAtomicApi::class)
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
@OptIn(ExperimentalAtomicApi::class)
public fun <T> Future<T>.getBlocking(timeout: Duration): Try<T> {
    if (isReloadMainThread) throw IllegalStateException("Cannot call getBlocking() from the 'reloadMainThread'")
    val javaFuture = java.util.concurrent.CompletableFuture<Try<T>>()
    invokeOnCompletion { javaFuture.complete(it) }
    return try {
        javaFuture.get(timeout.inWholeMilliseconds, java.util.concurrent.TimeUnit.MILLISECONDS)
    } catch (t: TimeoutException) {
        return t.toRight()
    }
}
