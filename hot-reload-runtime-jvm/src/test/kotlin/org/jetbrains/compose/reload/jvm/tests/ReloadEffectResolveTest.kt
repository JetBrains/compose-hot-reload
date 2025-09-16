/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.jvm.tests

import androidx.compose.runtime.Composable
import org.jetbrains.compose.devtools.api.ReloadEffect
import org.jetbrains.compose.devtools.api.ReloadState
import org.jetbrains.compose.reload.jvm.resolve
import kotlin.test.Test
import kotlin.test.assertEquals

class ReloadEffectResolveTest {
    @Test
    fun `test - same priorities`() {
        val effects = listOf(
            effect("A", priority = 0, ordinal = 2),
            effect("B", priority = 0, ordinal = 1),
            effect("C", priority = 0, ordinal = 0),
        )

        assertEquals(
            listOf("C", "B", "A"), effects.resolve(ReloadState.default).map { it.id }
        )
    }

    @Test
    fun `test  - different priorities`() {
        val effects = listOf(
            effect("low", priority = 0, ordinal = 0),
            effect("low", priority = 0, ordinal = 0),
            effect("high", priority = 1, ordinal = 1),
            effect("high", priority = 1, ordinal = 1),
            effect("first", priority = 1, ordinal = 0)
        )

        assertEquals(
            listOf("first", "high", "high"), effects.resolve(ReloadState.default).map { it.id }
        )
    }

    private fun effect(id: Any, priority: Int, ordinal: Int) = TestEffectImpl(id, priority, ordinal)

    private class TestEffectImpl(
        val id: Any,
        private val priority: Int,
        private val ordinal: Int,
    ) : ReloadEffect.OverlayEffect {

        override fun priority(state: ReloadState): ReloadEffect.Priority {
            return ReloadEffect.Priority(priority)
        }

        override fun ordinal(state: ReloadState): ReloadEffect.Ordinal {
            return ReloadEffect.Ordinal(ordinal)
        }

        @Composable
        override fun effectOverlay(state: ReloadState) = Unit
    }
}
