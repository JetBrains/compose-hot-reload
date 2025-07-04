/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.core

public sealed interface Context {
    public interface Key<out T> {
        public abstract class Optional<T> : Key<T?> where T : Any {
            final override val default: T? get() = null
        }

        public val default: T
    }

    public operator fun <T> get(key: Key<T>): T
}

public abstract class ContextElement : Context {
    public abstract val key: Context.Key<*>

    override fun <T> get(key: Context.Key<T>): T {
        @Suppress("UNCHECKED_CAST")
        return if (key == this.key) this as T
        else key.default
    }
}

public fun Context(): Context {
    return EmptyContext
}

public fun Context(vararg entries: ContextEntry<*>): Context {
    return if (entries.isEmpty()) Context()
    else ContextImpl(entries.associate { it.key to it.value })
}

public fun Context.with(vararg entries: ContextEntry<*>): Context {
    if (entries.isEmpty()) return this
    return ContextImpl(this.map + entries.associate { it.key to it.value })
}

public operator fun Context.plus(other: Context): Context {
    val thisMap = this.map
    val otherMap = other.map
    if (thisMap.isEmpty()) return other
    if (otherMap.isEmpty()) return this
    return ContextImpl(thisMap + otherMap)
}

public infix fun <T> Context.Key<T>.with(value: T): ContextEntry<T> =
    ContextEntry(this, value)

public class ContextEntry<T>(
    public val key: Context.Key<T>,
    public val value: T,
)

public object EmptyContext : Context {
    override fun <T> get(key: Context.Key<T>): T {
        return key.default
    }
}

private class ContextImpl(
    val map: Map<Context.Key<*>, Any?>
) : Context {
    override fun <T> get(key: Context.Key<T>): T {
        @Suppress("UNCHECKED_CAST")
        return if (key in map) map[key] as T
        else key.default
    }
}

public fun Context.asMap(): Map<Context.Key<*>, Any?> = this.map

private val Context.map: Map<Context.Key<*>, Any?>
    get() = when (this) {
        is ContextImpl -> this.map
        is ContextElement -> mapOf(key to this)
        is EmptyContext -> emptyMap()
    }
