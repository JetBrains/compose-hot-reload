/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.core

import java.util.concurrent.atomic.AtomicReference


public interface State<T> {
    public val value: T
    public suspend fun collect(collector: suspend (T) -> Unit)
}

public class MutableState<T>(initialValue: T) : State<T> {
    private val _value = AtomicReference(State(initialValue, Future<T>()))
    override val value: T get() = _value.get().value

    public fun update(updater: (T) -> T): Update<T> {
        val (previous, updated) = _value.update { current -> State(updater(current.value)) }
        previous.nextState.complete(updated.value)
        return Update(previous.value, updated.value)
    }

    public fun compareAndSet(expected: T, newValue: T): Boolean {
        val currentState = _value.get()
        if (currentState.value != expected) return false
        if (_value.compareAndSet(currentState, State(newValue))) {
            currentState.nextState.complete(newValue)
            return true
        }

        return false
    }

    override suspend fun collect(collector: suspend (T) -> Unit) {
        var lastEmittedValue: T? = null

        while (isActive()) {
            val state = _value.get()
            if (lastEmittedValue != state.value) {
                lastEmittedValue = state.value
                collector(state.value)
            }
            state.nextState.await()
        }
    }

    internal data class State<T>(val value: T, val nextState: CompletableFuture<T> = Future<T>())
}

public fun <T, R> State<T>.map(transform: (T) -> R): State<R> = object : State<R> {
    override val value: R get() = transform(this@map.value)

    override suspend fun collect(collector: suspend (R) -> Unit) {
        this@map.collect { value -> collector(transform(value)) }
    }
}
