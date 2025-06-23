/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.core

public interface Context {
    public operator fun <T> get(key: ContextKey<T>): T
    public fun <T> with(key: ContextKey<T>, value: T): Context
}

public interface ContextKey<T> {
    public val default: T
}

public fun Context(): Context {
    return ContextImpl.EMPTY
}

private class ContextImpl(
    private val map: Map<ContextKey<*>, Any?>
) : Context {
    override fun <T> get(key: ContextKey<T>): T {
        @Suppress("UNCHECKED_CAST")
        return if (key in map) map[key] as T
        else key.default
    }

    override fun <T> with(key: ContextKey<T>, value: T): Context {
        return ContextImpl(map.plus(key to value))
    }

    companion object {
        val EMPTY = ContextImpl(emptyMap())
    }
}
