/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.core

import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.AtomicReference as KAtomicReference

public data class Update<T>(val previous: T, val updated: T)

public inline fun <T> AtomicReference<T>.update(updater: (T) -> T): Update<T> {
    while (true) {
        val value = get()
        val updated = updater(value)
        if (compareAndSet(value, updated)) {
            return Update(value, updated)
        }
    }
}


@OptIn(ExperimentalAtomicApi::class)
@JvmName("updateKotlin")
public inline fun <T> KAtomicReference<T>.update(updater: (T) -> T): Update<T> {
    while (true) {
        val value = load()
        val updated = updater(value)
        if (compareAndSet(value, updated)) {
            return Update(value, updated)
        }
    }
}

@OptIn(ExperimentalAtomicApi::class)
@JvmName("updateKotlin")
public inline fun <T, R> KAtomicReference<T>.loop(action: (T) -> R): R {
    while (true) {
        val value = load()
        return action(value)
    }
}
