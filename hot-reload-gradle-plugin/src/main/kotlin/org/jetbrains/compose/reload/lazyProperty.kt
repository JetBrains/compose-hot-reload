/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload

import org.gradle.api.Project
import org.jetbrains.kotlin.tooling.core.HasMutableExtras
import org.jetbrains.kotlin.tooling.core.extrasKeyOf
import org.jetbrains.kotlin.tooling.core.getOrPut
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

internal fun <R : HasMutableExtras, T> lazyProperty(initializer: R.() -> T): ReadOnlyProperty<R, T> {
    return LazyProperty(initializer)
}

internal class LazyProperty<R : HasMutableExtras, T>(
    private val initializer: (R.() -> T)
) : ReadOnlyProperty<R, T> {

    override fun getValue(thisRef: R, property: KProperty<*>): T {
        val properties = thisRef.extras.getOrPut(propertiesKey) { mutableMapOf<LazyProperty<*, *>, Any?>() }
        @Suppress("UNCHECKED_CAST")
        return properties.getOrPut(this) {
            initializer.invoke(thisRef)
        } as T
    }

    private companion object {
        val propertiesKey = extrasKeyOf<MutableMap<LazyProperty<*, *>, Any?>>()
    }
}

internal fun <T> lazyProjectProperty(initializer: Project.() -> T): ReadOnlyProperty<Project, T> {
    return LazyProjectProperty(initializer)
}

private class LazyProjectProperty<T>(
    private val initializer: (Project.() -> T)
) : ReadOnlyProperty<Project, T> {

    companion object {
        private const val KEY = "org.jetbrains.compose.reload.extras"
    }

    @Suppress("UNCHECKED_CAST")
    override fun getValue(thisRef: Project, property: KProperty<*>): T {
        val extraProperties = thisRef.extensions.extraProperties

        val values = if (extraProperties.has(KEY)) {
            extraProperties.get(KEY) as MutableMap<LazyProjectProperty<*>, Any?>
        } else {
            val map = mutableMapOf<LazyProjectProperty<*>, Any?>()
            extraProperties.set(KEY, map)
            map
        }

        if (this in values) return values[this] as T

        val newValue = initializer.invoke(thisRef)
        values[this] = newValue
        return newValue
    }
}
