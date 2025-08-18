/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.orchestration

import org.jetbrains.compose.reload.core.getOrThrow
import org.jetbrains.compose.reload.core.type
import kotlin.test.Test
import kotlin.test.assertEquals

class OrchestrationStateIdTest {

    @Test
    fun `test - encode decode`() {
        val a = OrchestrationStateId(type<TestOrchestrationState>(), "a")
        assertEquals(a, a.encodeToByteArray().decodeOrchestrationStateId().getOrThrow())

        val b = OrchestrationStateId(type<TestOrchestrationState>(), "b")
        assertEquals(b, b.encodeToByteArray().decodeOrchestrationStateId().getOrThrow())

        val noName = OrchestrationStateId(type<TestOrchestrationState>())
        assertEquals(noName, noName.encodeToByteArray().decodeOrchestrationStateId().getOrThrow())
    }
}
