/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.orchestration

import org.jetbrains.compose.reload.core.getOrThrow
import org.jetbrains.compose.reload.orchestration.OrchestrationLoggerState.LoggerId
import kotlin.test.Test
import kotlin.test.assertEquals

class OrchestrationLoggerStateTest {
    @Test
    fun `test - encode decode`() {
        val encoder = encoderOfOrThrow<OrchestrationLoggerState>()

        val empty = OrchestrationLoggerState(emptySet())
        assertEquals(empty, encoder.decode(encoder.encode(empty)).getOrThrow())

        val nonEmpty = OrchestrationLoggerState(setOf(LoggerId("a"), LoggerId("b")))
        assertEquals(nonEmpty, encoder.decode(encoder.encode(nonEmpty)).getOrThrow())
    }
}
