/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.orchestration

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class OrchestrationServerStatesTest {

    private val keyA = stateKey<TestOrchestrationState>(
        "a", default = TestOrchestrationState(0)
    )

    private val keyB = stateKey(
        "b", default = TestOrchestrationState(0)
    )

    @Test
    fun `test - update in decoded form`() = runTest {
        val states = OrchestrationServerStates()
        val stateA = states.get(keyA)
        val stateB = states.get(keyB)

        assertEquals(keyA.default, stateA.value)
        assertEquals(keyB.default, stateB.value)

        states.update(keyA) { current ->
            TestOrchestrationState(current.payload + 1)
        }

        assertEquals(TestOrchestrationState(1), stateA.value)
        assertEquals(TestOrchestrationState(0), stateB.value)

        states.update(keyB) { current ->
            TestOrchestrationState(current.payload + 2)
        }

        assertEquals(TestOrchestrationState(1), stateA.value)
        assertEquals(TestOrchestrationState(2), stateB.value)
    }
}
