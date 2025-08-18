/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
@file:OptIn(ExperimentalTime::class)


package org.jetbrains.compose.devtools.api

import org.jetbrains.compose.reload.core.Try
import org.jetbrains.compose.reload.core.Type
import org.jetbrains.compose.reload.core.decode
import org.jetbrains.compose.reload.core.readOptionalFrame
import org.jetbrains.compose.reload.core.readString
import org.jetbrains.compose.reload.core.type
import org.jetbrains.compose.reload.core.writeOptionalFrame
import org.jetbrains.compose.reload.core.writeString
import org.jetbrains.compose.reload.orchestration.OrchestrationMessageId
import org.jetbrains.compose.reload.orchestration.OrchestrationState
import org.jetbrains.compose.reload.orchestration.OrchestrationStateEncoder
import org.jetbrains.compose.reload.orchestration.OrchestrationStateKey
import org.jetbrains.compose.reload.orchestration.stateKey
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

public sealed class ReloadState : OrchestrationState {

    public abstract val time: Instant

    public data class Ok(
        override val time: Instant = Clock.System.now(),
    ) : ReloadState()

    public data class Reloading(
        override val time: Instant = Clock.System.now(),
        val reloadRequestId: OrchestrationMessageId? = null
    ) : ReloadState()

    public data class Failed(
        override val time: Instant = Clock.System.now(), val reason: String,
    ) : ReloadState()

    public companion object {
        public val key: OrchestrationStateKey<ReloadState> = stateKey(
            default = Ok()
        )
    }
}

internal class ReloadStateEncoder : OrchestrationStateEncoder<ReloadState> {
    override val type: Type<ReloadState> = type()

    private companion object {
        private const val TYPE_OK = 0
        private const val TYPE_RELOADING = 1
        private const val TYPE_FAILED = 2
    }

    override fun encode(state: ReloadState): ByteArray = when (state) {
        is ReloadState.Ok -> org.jetbrains.compose.reload.core.encode {
            writeByte(TYPE_OK)
            writeLong(state.time.toEpochMilliseconds())
        }

        is ReloadState.Failed -> org.jetbrains.compose.reload.core.encode {
            writeByte(TYPE_FAILED)
            writeLong(state.time.toEpochMilliseconds())
            writeString(state.reason)
        }

        is ReloadState.Reloading -> org.jetbrains.compose.reload.core.encode {
            writeByte(TYPE_RELOADING)
            writeLong(state.time.toEpochMilliseconds())
            writeOptionalFrame(state.reloadRequestId?.encodeToByteArray())
        }
    }

    override fun decode(data: ByteArray): Try<ReloadState> = Try {
        data.decode {
            when (readByte().toInt()) {
                TYPE_OK -> ReloadState.Ok(Instant.fromEpochMilliseconds(readLong()))
                TYPE_RELOADING -> ReloadState.Reloading(
                    time = Instant.fromEpochMilliseconds(readLong()),
                    reloadRequestId = readOptionalFrame()?.let(::OrchestrationMessageId)
                )
                TYPE_FAILED -> ReloadState.Failed(Instant.fromEpochMilliseconds(readLong()), readString())
                else -> throw IllegalArgumentException("Unknown type: $type")
            }
        }
    }
}
