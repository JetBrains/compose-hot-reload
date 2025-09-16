/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.devtools.api

import org.jetbrains.compose.reload.InternalHotReloadApi
import org.jetbrains.compose.reload.core.Try
import org.jetbrains.compose.reload.core.Type
import org.jetbrains.compose.reload.core.encodeByteArray
import org.jetbrains.compose.reload.core.toLeft
import org.jetbrains.compose.reload.core.tryDecode
import org.jetbrains.compose.reload.core.type
import org.jetbrains.compose.reload.orchestration.OrchestrationState
import org.jetbrains.compose.reload.orchestration.OrchestrationStateEncoder
import org.jetbrains.compose.reload.orchestration.OrchestrationStateId
import org.jetbrains.compose.reload.orchestration.OrchestrationStateKey
import org.jetbrains.compose.reload.orchestration.stateId
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

/**
 * Used for controlling the virtual time of headless applications
 */
@InternalHotReloadApi
public data class VirtualTimeState(val time: Duration) : OrchestrationState {
    public companion object Key : OrchestrationStateKey<VirtualTimeState?>() {
        override val id: OrchestrationStateId<VirtualTimeState?> = stateId()
        override val default: VirtualTimeState? = null
    }
}

internal class VirtualTimeStateEncoder : OrchestrationStateEncoder<VirtualTimeState?> {
    override val type: Type<VirtualTimeState?> = type()

    override fun encode(state: VirtualTimeState?): ByteArray {
        if (state == null) return byteArrayOf()
        return encodeByteArray {
            writeLong(state.time.inWholeNanoseconds)
        }
    }

    override fun decode(data: ByteArray): Try<VirtualTimeState?> {
        if (data.isEmpty()) return null.toLeft()
        return data.tryDecode {
            VirtualTimeState(readLong().nanoseconds)
        }
    }
}
