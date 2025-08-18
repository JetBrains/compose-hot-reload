/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.devtools.api

import org.jetbrains.compose.reload.core.Try
import org.jetbrains.compose.reload.core.Type
import org.jetbrains.compose.reload.core.tryDecode
import org.jetbrains.compose.reload.core.type
import org.jetbrains.compose.reload.orchestration.OrchestrationState
import org.jetbrains.compose.reload.orchestration.OrchestrationStateEncoder
import org.jetbrains.compose.reload.orchestration.OrchestrationStateKey
import org.jetbrains.compose.reload.orchestration.stateKey

public data class ReloadCountState(
    val successfulReloads: Int = 0,
    val failedReloads: Int = 0
) : OrchestrationState {

    public companion object {
        public val key: OrchestrationStateKey<ReloadCountState> = stateKey(
            default = ReloadCountState()
        )
    }
}

internal class ReloadCountStateEncoder : OrchestrationStateEncoder<ReloadCountState> {
    override val type: Type<ReloadCountState> = type()

    override fun encode(state: ReloadCountState): ByteArray = org.jetbrains.compose.reload.core.encodeByteArray {
        writeInt(state.successfulReloads)
        writeInt(state.failedReloads)
    }

    override fun decode(data: ByteArray): Try<ReloadCountState> = data.tryDecode {
        ReloadCountState(readInt(), readInt())
    }
}
