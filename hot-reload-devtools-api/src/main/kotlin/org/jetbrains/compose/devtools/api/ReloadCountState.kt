/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.devtools.api

import org.jetbrains.compose.reload.core.Try
import org.jetbrains.compose.reload.core.Type
import org.jetbrains.compose.reload.core.decode
import org.jetbrains.compose.reload.core.encodeByteArray
import org.jetbrains.compose.reload.core.readFields
import org.jetbrains.compose.reload.core.tryDecode
import org.jetbrains.compose.reload.core.type
import org.jetbrains.compose.reload.core.writeFields
import org.jetbrains.compose.reload.orchestration.OrchestrationState
import org.jetbrains.compose.reload.orchestration.OrchestrationStateEncoder
import org.jetbrains.compose.reload.orchestration.OrchestrationStateId
import org.jetbrains.compose.reload.orchestration.OrchestrationStateKey
import org.jetbrains.compose.reload.orchestration.stateId

public class ReloadCountState(
    public val successfulReloads: Int = 0,
    public val failedReloads: Int = 0
) : OrchestrationState {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ReloadCountState) return false
        if (other.successfulReloads != successfulReloads) return false
        if (other.failedReloads != failedReloads) return false
        return true
    }

    override fun hashCode(): Int {
        var result = successfulReloads.hashCode()
        result = 31 * result + failedReloads.hashCode()
        return result
    }

    override fun toString(): String {
        return "ReloadCountState(successfulReloads=$successfulReloads, failedReloads=$failedReloads)"
    }

    public companion object Key : OrchestrationStateKey<ReloadCountState>() {
        override val id: OrchestrationStateId<ReloadCountState> = stateId()
        override val default: ReloadCountState = ReloadCountState()
    }
}

internal class ReloadCountStateEncoder : OrchestrationStateEncoder<ReloadCountState> {
    override val type: Type<ReloadCountState> = type()

    override fun encode(state: ReloadCountState): ByteArray = encodeByteArray {
        writeFields(
            "successfulReloads" to encodeByteArray { writeInt(state.successfulReloads) },
            "failedReloads" to encodeByteArray { writeInt(state.failedReloads) }
        )
    }

    override fun decode(data: ByteArray): Try<ReloadCountState> = data.tryDecode {
        val fields = readFields()
        ReloadCountState(
            fields["successfulReloads"]?.decode { readInt() } ?: error("Missing field `successfulReloads`"),
            fields["failedReloads"]?.decode { readInt() } ?: error("Missing field `failedReloads`")
        )
    }
}
