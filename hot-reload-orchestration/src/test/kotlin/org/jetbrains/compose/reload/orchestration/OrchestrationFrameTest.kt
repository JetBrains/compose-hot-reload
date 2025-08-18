/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.orchestration

import kotlin.test.Test
import kotlin.test.assertEquals

class OrchestrationFrameTest {
    @Test
    fun `test - OrchestrationStateRequest`() {
        val source = OrchestrationStateRequest(stateId<TestOrchestrationState>())
        assertEquals(source, source.encodeToFrame().data.decodeStateRequest())
    }

    @Test
    fun `test - OrchestrationStateUpdate`() {
        val source = OrchestrationStateUpdate(
            stateId = stateId<TestOrchestrationState>(),
            expectedValue = null,
            updatedValue = Binary(byteArrayOf(1, 2, 3, 4, 5)),
        )

        assertEquals(source, source.encodeToFrame().data.decodeStateUpdate())

        val withExpectedValue = source.copy(expectedValue = Binary(byteArrayOf(1, 2, 3)))
        assertEquals(withExpectedValue, withExpectedValue.encodeToFrame().data.decodeStateUpdate())
    }

    @Test
    fun `test - OrchestrationStateUpdateResponse`() {
        val source = OrchestrationStateUpdate.Response(true)
        assertEquals(source, source.encodeToFrame().data.decodeStateUpdateResponse())

        assertEquals(
            source.copy(accepted = false),
            source.copy(accepted = false).encodeToFrame().data.decodeStateUpdateResponse()
        )
    }

    @Test
    fun `test - OrchestrationStateValue`() {
        val source = OrchestrationStateValue(
            stateId = stateId<TestOrchestrationState>(),
            value = Binary(byteArrayOf(1, 2, 3, 4, 5)),
        )

        assertEquals(source, source.encodeToFrame().data.decodeStateValue())

        val withNullValue = source.copy(value = null)
        assertEquals(withNullValue, withNullValue.encodeToFrame().data.decodeStateValue())
    }
}
