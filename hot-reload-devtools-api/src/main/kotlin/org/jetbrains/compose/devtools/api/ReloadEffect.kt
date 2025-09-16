/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

@file:OptIn(ExperimentalHotReloadApi::class)

package org.jetbrains.compose.devtools.api

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.reload.ExperimentalHotReloadApi


/**
 * Provides effects rendered in the user application.
 * A given implementation class has to be registered as Service in
 * `META-INF/services/org.jetbrains.compose.devtools.api.ReloadEffect`
 */
@ExperimentalHotReloadApi
public sealed interface ReloadEffect {

    /**
     * See [ReloadEffect.priority]
     */
    @ExperimentalHotReloadApi
    @JvmInline
    public value class Priority(private val value: Int) : Comparable<Priority> {
        override fun compareTo(other: Priority): Int {
            return value.compareTo(other.value)
        }

        @ExperimentalHotReloadApi
        public companion object Companion {
            public val normal: Priority = Priority(0)
            public val high: Priority = Priority(10)
        }
    }

    /**
     * See [ReloadEffect.ordinal]
     */
    @ExperimentalHotReloadApi
    @JvmInline
    public value class Ordinal(private val value: Int) : Comparable<Ordinal> {
        override fun compareTo(other: Ordinal): Int {
            return value.compareTo(other.value)
        }

        public operator fun plus(value: Int): Ordinal = Ordinal(this.value + value)
        public operator fun minus(value: Int): Ordinal = Ordinal(this.value - value)

        @ExperimentalHotReloadApi
        public companion object Companion {
            public val first: Ordinal = Ordinal(0)
            public val medium: Ordinal = Ordinal(128)
            public val last: Ordinal = Ordinal(Integer.MAX_VALUE / 2)
        }
    }

    /**
     * Defines the 'priority' of the effect as in:
     * Only the effect with 'highest priority' will be shown.
     * This means a group of effects using [Priority.high] would overwrite effects
     * using [Priority.normal]. None of the effects with [Priority.normal] will be shown.
     */
    public fun priority(state: ReloadState): Priority = Priority.normal

    /**
     * Used to sort all provided effects.
     * e.g. modifiers, provided by [ModifierEffect] will be applied in order, adhering to this [Ordinal].
     * Ordinals with lower values will be ordered first (e.g. [Ordinal.first]).
     * Ordinals with high values will be ordered last (e.g. [Ordinal.last])
     */
    public fun ordinal(state: ReloadState): Ordinal = Ordinal.medium

    /**
     * See [ReloadEffect] for registering the effect!
     *
     * A modifier-based effect can return a [androidx.compose.ui.Modifier] which will be applied
     * to the root of the window of the user application.
     */
    @ExperimentalHotReloadApi
    public interface ModifierEffect : ReloadEffect {
        @Composable
        public fun effectModifier(state: ReloadState): Modifier
    }

    /**
     * See [org.jetbrains.compose.reload.jvm.HotReloadEffects] for registering the effect!
     *
     * An overlay, rendered over the root of the window of the user application.
     * Multiple overlay-based effects likely want to order themselves using the [ordinal] function.
     */
    @ExperimentalHotReloadApi
    public interface OverlayEffect : ReloadEffect {
        @Composable
        public fun effectOverlay(state: ReloadState)
    }
}
