/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.core

public interface ReloadContext {
    public operator fun <T> get(key: ContextKey<T>): T
    public fun <T> with(key: ContextKey<T>, value: T): ReloadContext
}

public interface ContextKey<T> {
    public val default: T
}

public fun Context(): ReloadContext {
    return ContextImpl.EMPTY
}

private class ContextImpl(
    private val map: Map<ContextKey<*>, Any?>
) : ReloadContext {
    override fun <T> get(key: ContextKey<T>): T {
        @Suppress("UNCHECKED_CAST")
        if (key in map) return map[key] as T
        else return key.default
    }

    override fun <T> with(key: ContextKey<T>, value: T): ReloadContext {
        return ContextImpl(map.plus(key to value))
    }

    companion object {
        val EMPTY = ContextImpl(emptyMap())
    }
}
