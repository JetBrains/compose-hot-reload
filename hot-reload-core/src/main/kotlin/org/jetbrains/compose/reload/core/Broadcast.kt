/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

@file:OptIn(ExperimentalAtomicApi::class)

package org.jetbrains.compose.reload.core

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.withLock
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume

public interface Broadcast<T> {
    public fun invokeOnValue(action: (T) -> Unit): Disposable
    public suspend fun send(value: T)
}

public suspend fun <T> Broadcast<T>.collect(action: suspend (T) -> Unit) {
    collectWhile { value -> action(value); true }
}

public suspend fun <T> Broadcast<T>.collectWhile(action: suspend (T) -> Boolean) {
    val lock = ReentrantLock()

    var waiting: Continuation<T>? = null
    val deque = ArrayDeque<T>()

    val disposable = invokeOnValue { value ->
        val continuation = lock.withLock {
            waiting ?: run {
                deque.addLast(value)
                return@invokeOnValue
            }
        }
        continuation.resume(value)
    }

    invokeOnStop {
        disposable.dispose()
    }

    while (isActive()) {
        val element = suspendStoppableCoroutine { continuation ->
            val next = lock.withLock {
                if (deque.isEmpty()) {
                    waiting = continuation
                    return@suspendStoppableCoroutine
                }

                deque.removeFirst()
            }

            continuation.resume(next)
        }

        if (!action(element)) break
    }
}


public fun <T> Broadcast(): Broadcast<T> {
    return BroadcastImpl()
}

private class BroadcastImpl<T> : Broadcast<T> {

    private val listeners = AtomicReference<List<(T) -> Unit>>(emptyList())

    override suspend fun send(value: T) {
        listeners.load().map { listener ->
            reloadMainThread.invoke { listener(value) }
        }.forEach { future ->
            future.await()
        }
    }

    override fun invokeOnValue(action: (T) -> Unit): Disposable {
        val wrappedAction = { value: T ->
            action(value)
        }

        listeners.update { it + wrappedAction }
        return Disposable {
            listeners.update { it - wrappedAction }
        }
    }
}
