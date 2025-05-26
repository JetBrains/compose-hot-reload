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
public interface Future<T> {
    /**
     * Registers a completion handler for this future.
     * If the future is already completed, the result handler will be called with the known value
     * If the future is not yet completed, the result handler will be called once the future completes.
     * The [onResult] will always be called on the [reloadMainThread]
     */
    public fun <R> invokeOnCompletion(onResult: (Result<T>) -> R): Future<R>
}

internal interface CompletableFuture<T> : Future<T> {
    fun completeWith(result: Result<T>)
}

internal fun <T> Future(): CompletableFuture<T> {
    return FutureImpl()
}

internal fun <T> Future(result: Result<T>): Future<T> {
    return CompletedFuture(result)
}

private class FutureImpl<T> : CompletableFuture<T> {
    private val lock = ReentrantLock()

    private val listeners = mutableListOf<(Result<T>) -> Unit>()

    @Volatile
    private var result: Result<T>? = null

    override fun <R> invokeOnCompletion(onResult: (Result<T>) -> R): Future<R> = lock.withLock {
        val result = result

        /* Fast path: Result is already known */
        if (result != null) {
            return reloadMainThread.invoke { onResult(result) }
        }

        /* Slow path: Create a future and wait for completion */
        val future = Future<R>()
        listeners.add { tResult ->
            reloadMainThread.invokeImmediate { onResult(tResult) }
                .invokeOnCompletion { result -> future.completeWith(result) }
        }
        future
    }


    override fun completeWith(result: Result<T>) = lock.withLock {
        this.result = result
        listeners.toTypedArray().forEach { listener -> listener(result) }
        listeners.clear()
    }
}

private class CompletedFuture<T>(val result: Result<T>) : Future<T> {
    override fun <R> invokeOnCompletion(onResult: (Result<T>) -> R): Future<R> {
        return reloadMainThread.invoke { onResult(result) }
    }
}

/**
 * Blocks the current thread until the result of the given future is known.
 * @throws IllegalStateException if this function is called from the [reloadMainThread]
 *  - This is done to prevent deadlocking the main thread. An exception is thrown because a failing
 *  result object has different semantics than this precondition.
 */
@OptIn(ExperimentalAtomicApi::class)
public fun <T> Future<T>.getBlocking(): Result<T> {
    if (isReloadMainThread) throw IllegalStateException("Cannot call getBlocking() from the 'reloadMainThread'")
    val javaFuture = java.util.concurrent.CompletableFuture<Result<T>>()
    invokeOnCompletion { javaFuture.complete(it) }
    return javaFuture.get()
}

/**
 * Same as [getBlocking], but with a given [timeout] which will return a failing Result, containing a
 * [TimeoutException] if the timeout is reached.]
 */
@OptIn(ExperimentalAtomicApi::class)
public fun <T> Future<T>.getBlocking(timeout: Duration): Result<T> {
    if (isReloadMainThread) throw IllegalStateException("Cannot call getBlocking() from the 'reloadMainThread'")
    val javaFuture = java.util.concurrent.CompletableFuture<Result<T>>()
    invokeOnCompletion { javaFuture.complete(it) }
    return try {
        javaFuture.get(timeout.inWholeMilliseconds, java.util.concurrent.TimeUnit.MILLISECONDS)
    } catch (t: TimeoutException) {
        return Result.failure(t)
    }
}
