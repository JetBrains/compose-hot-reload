/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.analysis

import java.lang.ref.WeakReference
import java.util.WeakHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.collections.set
import kotlin.concurrent.read
import kotlin.concurrent.write

private class Interner<T> {
    private val lock = ReentrantReadWriteLock()
    private val map = WeakHashMap<T, WeakReference<T>>()

    fun intern(value: T): T {
        val existing = lock.read { map[value]?.get() }
        if (existing != null) return existing

        return lock.write {
            /* Check that the value was not added while we waited for the write lock */
            val added = map[value]?.get()
            if (added != null) return@write added

            map[value] = WeakReference(value)
            value
        }
    }
}

private val StringInterner = Interner<String>()

internal fun String.interned(): String = StringInterner.intern(this)
