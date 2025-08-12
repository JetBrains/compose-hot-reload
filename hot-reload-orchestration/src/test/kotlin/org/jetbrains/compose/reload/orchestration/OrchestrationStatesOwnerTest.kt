/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.orchestration

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class OrchestrationStatesOwnerTest {

    data class TestState(val value: Int) : OrchestrationState

    private val keyA = OrchestrationStateKey<TestState>(
        id = OrchestrationStateId(Type<TestState>(), "a"),
        default = TestState(0)
    )

    private val keyB = OrchestrationStateKey<TestState>(
        id = OrchestrationStateId(Type<TestState>(), "b"),
        default = TestState(0)
    )

    @Test
    fun `test - update in decoded form`() = runTest {
        val states = OrchestrationStatesOwner()
        val stateA = states.get(keyA)
        val stateB = states.get(keyB)

        assertEquals(keyA.default, stateA.value)
        assertEquals(keyB.default, stateB.value)

        states.update(keyA) { current ->
            TestState(current.value + 1)
        }

        assertEquals(TestState(1), stateA.value)
        assertEquals(TestState(0), stateB.value)

        states.update(keyB) { current ->
            TestState(current.value + 2)
        }

        assertEquals(TestState(1), stateA.value)
        assertEquals(TestState(2), stateB.value)
    }
}
