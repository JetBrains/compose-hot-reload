/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.orchestration.utils

import org.jetbrains.compose.reload.core.Try
import org.jetbrains.compose.reload.core.type
import org.jetbrains.compose.reload.orchestration.OrchestrationState
import org.jetbrains.compose.reload.orchestration.OrchestrationStateEncoder
import org.jetbrains.compose.reload.orchestration.stateKey
import java.io.Serializable
import java.nio.ByteBuffer

data class TestOrchestrationState(val payload: Int) : OrchestrationState, Serializable

class TestOrchestrationStateEncoder : OrchestrationStateEncoder<TestOrchestrationState> {
    override val type = type<TestOrchestrationState>()

    override fun encode(state: TestOrchestrationState): ByteArray {
        return ByteBuffer.allocate(Int.SIZE_BYTES).putInt(state.payload).array()
    }

    override fun decode(data: ByteArray): Try<TestOrchestrationState> = Try {
        TestOrchestrationState(ByteBuffer.wrap(data).getInt())
    }
}

val stateKey = stateKey<TestOrchestrationState>(
    default = TestOrchestrationState(0)
)
