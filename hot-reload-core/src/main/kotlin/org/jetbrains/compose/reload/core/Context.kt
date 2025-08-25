/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.core

import org.jetbrains.compose.reload.DelicateHotReloadApi

@DelicateHotReloadApi
public sealed interface Context {
    public interface Key<out T> {
        public abstract class Optional<T> : Key<T?> where T : Any {
            final override val default: T? get() = null
        }

        public val default: T
    }

    public operator fun <T> get(key: Key<T>): T
}

@DelicateHotReloadApi
public abstract class ContextElement : Context {
    public abstract val key: Context.Key<*>

    override fun <T> get(key: Context.Key<T>): T {
        @Suppress("UNCHECKED_CAST")
        return if (key == this.key) this as T
        else key.default
    }
}

@DelicateHotReloadApi
public fun Context(): Context {
    return EmptyContext
}

@DelicateHotReloadApi
public fun Context(vararg entries: ContextEntry<*>): Context {
    return if (entries.isEmpty()) Context()
    else ContextImpl(entries.associate { it.key to it.value })
}

@DelicateHotReloadApi
public fun Context.with(vararg entries: ContextEntry<*>): Context {
    if (entries.isEmpty()) return this
    return ContextImpl(this.map + entries.associate { it.key to it.value })
}

@DelicateHotReloadApi
public operator fun Context.plus(other: Context): Context {
    val thisMap = this.map
    val otherMap = other.map
    if (thisMap.isEmpty()) return other
    if (otherMap.isEmpty()) return this
    return ContextImpl(thisMap + otherMap)
}

@DelicateHotReloadApi
public infix fun <T> Context.Key<T>.with(value: T): ContextEntry<T> =
    ContextEntry(this, value)

@DelicateHotReloadApi
public class ContextEntry<T>(
    public val key: Context.Key<T>,
    public val value: T,
)

@DelicateHotReloadApi
public object EmptyContext : Context {
    override fun <T> get(key: Context.Key<T>): T {
        return key.default
    }
}

@DelicateHotReloadApi
private class ContextImpl(
    val map: Map<Context.Key<*>, Any?>
) : Context {
    override fun <T> get(key: Context.Key<T>): T {
        @Suppress("UNCHECKED_CAST")
        return if (key in map) map[key] as T
        else key.default
    }
}

@DelicateHotReloadApi
public fun Context.asMap(): Map<Context.Key<*>, Any?> = this.map

@DelicateHotReloadApi
private val Context.map: Map<Context.Key<*>, Any?>
    get() = when (this) {
        is ContextImpl -> this.map
        is ContextElement -> mapOf(key to this)
        is EmptyContext -> emptyMap()
    }
