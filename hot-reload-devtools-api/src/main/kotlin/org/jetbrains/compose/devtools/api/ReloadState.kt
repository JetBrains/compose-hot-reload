/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
@file:OptIn(ExperimentalTime::class)


package org.jetbrains.compose.devtools.api

import org.jetbrains.compose.reload.core.Try
import org.jetbrains.compose.reload.core.Type
import org.jetbrains.compose.reload.core.decode
import org.jetbrains.compose.reload.core.encodeByteArray
import org.jetbrains.compose.reload.core.readFields
import org.jetbrains.compose.reload.core.tryDecode
import org.jetbrains.compose.reload.core.type
import org.jetbrains.compose.reload.core.writeFields
import org.jetbrains.compose.reload.orchestration.OrchestrationMessageId
import org.jetbrains.compose.reload.orchestration.OrchestrationState
import org.jetbrains.compose.reload.orchestration.OrchestrationStateEncoder
import org.jetbrains.compose.reload.orchestration.OrchestrationStateId
import org.jetbrains.compose.reload.orchestration.OrchestrationStateKey
import org.jetbrains.compose.reload.orchestration.stateId
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

public sealed class ReloadState : OrchestrationState {

    public abstract val time: Instant

    public class Ok(
        override val time: Instant = Clock.System.now(),
    ) : ReloadState() {

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Ok) return false
            if (time != other.time) return false
            return true
        }

        override fun hashCode(): Int {
            var result = "Ok".hashCode()
            result = 31 * result + time.hashCode()
            return result
        }

        override fun toString(): String {
            return "Ok($time)"
        }
    }

    public class Reloading(
        override val time: Instant = Clock.System.now(),
        public val reloadRequestId: OrchestrationMessageId? = null
    ) : ReloadState() {

        public fun copy(reloadRequestId: OrchestrationMessageId?): Reloading {
            return Reloading(time, reloadRequestId)
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Reloading) return false
            if (time != other.time) return false
            if (reloadRequestId != other.reloadRequestId) return false
            return true
        }

        override fun hashCode(): Int {
            var result = "Reloading".hashCode()
            result = 31 * result + time.hashCode()
            result = 31 * result + (reloadRequestId?.hashCode() ?: 0)
            return result
        }

        override fun toString(): String {
            return "Reloading($time, reloadRequestId=$reloadRequestId)"
        }
    }

    public class Failed(
        override val time: Instant = Clock.System.now(),
        public val reason: String,
    ) : ReloadState() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Failed) return false
            if (time != other.time) return false
            if (reason != other.reason) return false
            return true
        }

        override fun hashCode(): Int {
            var result = "Failed".hashCode()
            result = 31 * result + time.hashCode()
            result = 31 * result + reason.hashCode()
            return result
        }

        override fun toString(): String {
            return "Failed($reason)"
        }
    }

    public companion object Key : OrchestrationStateKey<ReloadState>() {
        override val id: OrchestrationStateId<ReloadState> = stateId()
        override val default: ReloadState by lazy { Ok() }
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
        is ReloadState.Ok -> encodeByteArray {
            writeByte(TYPE_OK)
            writeFields(
                "time" to encodeByteArray { writeLong(state.time.toEpochMilliseconds()) },
            )
        }

        is ReloadState.Failed -> encodeByteArray {
            writeByte(TYPE_FAILED)

            writeFields(
                "time" to encodeByteArray { writeLong(state.time.toEpochMilliseconds()) },
                "reason" to state.reason.encodeToByteArray(),
            )
        }

        is ReloadState.Reloading -> encodeByteArray {
            writeByte(TYPE_RELOADING)

            writeFields(
                "time" to encodeByteArray { writeLong(state.time.toEpochMilliseconds()) },
                "reloadRequestId" to state.reloadRequestId?.encodeToByteArray(),
            )
        }
    }

    override fun decode(data: ByteArray): Try<ReloadState> = data.tryDecode {
        when (readByte().toInt()) {
            TYPE_OK -> {
                val fields = readFields()
                ReloadState.Ok(
                    time = fields["time"]?.decode { Instant.fromEpochMilliseconds(readLong()) }
                        ?: error("Missing 'time' field")
                )
            }
            TYPE_RELOADING -> {
                val fields = readFields()
                ReloadState.Reloading(
                    time = fields["time"]?.decode { Instant.fromEpochMilliseconds(readLong()) }
                        ?: error("Missing 'time' field"),

                    reloadRequestId = fields["reloadRequestId"]?.let(::OrchestrationMessageId)
                )
            }
            TYPE_FAILED -> {
                val fields = readFields()
                return@tryDecode ReloadState.Failed(
                    time = Instant.fromEpochMilliseconds(fields["time"]?.decode { readLong() }
                        ?: error("Missing 'time' field")),

                    reason = fields["reason"]?.decodeToString()
                        ?: error("Missing 'reason' field"),
                )
            }
            else -> throw IllegalArgumentException("Unknown type: $type")
        }
    }
}
