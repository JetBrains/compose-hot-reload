/*
 * Copyright 2024-2026 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.devtools.api

import org.jetbrains.compose.devtools.api.UIErrorState.UIError
import org.jetbrains.compose.reload.ExperimentalHotReloadApi
import org.jetbrains.compose.reload.core.Try
import org.jetbrains.compose.reload.core.Type
import org.jetbrains.compose.reload.core.WindowId
import org.jetbrains.compose.reload.core.decode
import org.jetbrains.compose.reload.core.encodeByteArray
import org.jetbrains.compose.reload.core.readFields
import org.jetbrains.compose.reload.core.readStringList
import org.jetbrains.compose.reload.core.tryDecode
import org.jetbrains.compose.reload.core.type
import org.jetbrains.compose.reload.core.writeFields
import org.jetbrains.compose.reload.core.writeStringList
import org.jetbrains.compose.reload.orchestration.OrchestrationState
import org.jetbrains.compose.reload.orchestration.OrchestrationStateEncoder
import org.jetbrains.compose.reload.orchestration.OrchestrationStateId
import org.jetbrains.compose.reload.orchestration.OrchestrationStateKey
import org.jetbrains.compose.reload.orchestration.stateId

/**
 * Runtime UI exceptions currently shown by the application, keyed by [WindowId].
 *
 * An entry is present while the composition of that window is failing (reported by the runtime when
 * invoking the development entry point throws) and is removed once the window renders successfully
 * again. This differs from a reload failure (see [ReloadState.Failed]): a [UIError] is an exception
 * thrown while building the UI itself.
 */
@ExperimentalHotReloadApi
public class UIErrorState(
    public val errors: Map<WindowId, UIError>
) : OrchestrationState {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UIErrorState) return false
        if (errors != other.errors) return false
        return true
    }

    override fun hashCode(): Int {
        return errors.hashCode()
    }

    override fun toString(): String {
        return "UIErrorState($errors)"
    }

    public class UIError(
        public val message: String?,
        public val stacktrace: List<String>,
    ) {
        private val hashCode: Int = run {
            var result = message?.hashCode() ?: 0
            result = 31 * result + stacktrace.hashCode()
            result
        }

        override fun hashCode(): Int {
            return hashCode
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is UIError) return false
            if (other.hashCode() != hashCode) return false
            if (this.message != other.message) return false
            if (this.stacktrace != other.stacktrace) return false
            return true
        }

        override fun toString(): String {
            return "UIError(message=$message)"
        }
    }

    public companion object Key : OrchestrationStateKey<UIErrorState>() {
        override val id: OrchestrationStateId<UIErrorState> = stateId()
        override val default: UIErrorState = UIErrorState(emptyMap())
    }
}

internal class UIErrorStateEncoder : OrchestrationStateEncoder<UIErrorState> {
    override val type: Type<UIErrorState> = type()

    override fun encode(state: UIErrorState): ByteArray = encodeByteArray {
        writeInt(state.errors.size)
        state.errors.forEach { (windowId, error) ->
            writeFields(
                "windowId" to windowId.value.encodeToByteArray(),
                "message" to error.message?.encodeToByteArray(),
                "stacktrace" to encodeByteArray { writeStringList(error.stacktrace) },
            )
        }
    }

    override fun decode(data: ByteArray): Try<UIErrorState> = data.tryDecode {
        val count = readInt()
        val errors = buildMap {
            repeat(count) {
                val fields = readFields()
                this += WindowId(fields["windowId"]?.decodeToString() ?: error("Missing 'windowId'")) to UIError(
                    // Missing or absent message decodes to null.
                    message = fields["message"]?.decodeToString(),
                    stacktrace = fields["stacktrace"]?.decode { readStringList() } ?: emptyList(),
                )
            }
        }
        UIErrorState(errors)
    }
}
