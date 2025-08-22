/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.devtools.api

import org.jetbrains.compose.devtools.api.WindowsState.WindowState
import org.jetbrains.compose.reload.core.Try
import org.jetbrains.compose.reload.core.Type
import org.jetbrains.compose.reload.core.WindowId
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

public class WindowsState(
    public val windows: Map<WindowId, WindowState>
) : OrchestrationState {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WindowsState) return false
        if (windows != other.windows) return false
        return true
    }

    override fun hashCode(): Int {
        return windows.hashCode()
    }

    override fun toString(): String {
        return "WindowsState($windows)"
    }

    public class WindowState(
        public val x: Int,
        public val y: Int,
        public val width: Int,
        public val height: Int,
        public val isAlwaysOnTop: Boolean
    ) {
        private val hashCode: Int = run {
            var result = x.hashCode()
            result = 31 * result + y.hashCode()
            result = 31 * result + width
            result = 31 * result + height
            result = 31 * result + isAlwaysOnTop.hashCode()
            result
        }

        override fun hashCode(): Int {
            return hashCode
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is WindowState) return false
            if (other.hashCode() != hashCode) return false
            if (this.x != other.x) return false
            if (this.y != other.y) return false
            if (this.width != other.width) return false
            if (this.height != other.height) return false
            if (this.isAlwaysOnTop != other.isAlwaysOnTop) return false
            return true
        }

        override fun toString(): String {
            return "WindowState(x=$x, y=$y, width=$width, height=$height, isAlwaysOnTop=$isAlwaysOnTop)"
        }
    }

    public companion object Key : OrchestrationStateKey<WindowsState>() {
        override val id: OrchestrationStateId<WindowsState> = stateId()
        override val default: WindowsState = WindowsState(emptyMap())
    }
}

internal class WindowsStateEncoder : OrchestrationStateEncoder<WindowsState> {
    override val type: Type<WindowsState> = type()

    override fun encode(state: WindowsState): ByteArray = encodeByteArray {
        writeInt(state.windows.size)
        state.windows.forEach { (windowId, windowState) ->
            writeFields(
                "windowId" to windowId.value.encodeToByteArray(),
                "x" to encodeByteArray { writeInt(windowState.x) },
                "y" to encodeByteArray { writeInt(windowState.y) },
                "width" to encodeByteArray { writeInt(windowState.width) },
                "height" to encodeByteArray { writeInt(windowState.height) },
                "isAlwaysOnTop" to encodeByteArray { writeBoolean(windowState.isAlwaysOnTop) }
            )
        }
    }

    override fun decode(data: ByteArray): Try<WindowsState> = data.tryDecode {
        val windows = readInt()
        val windowStates = buildMap {
            repeat(windows) {
                val fields = readFields()
                this += WindowId(fields["windowId"]?.decodeToString() ?: error("Missing 'windowId'")) to WindowState(
                    x = fields["x"]?.decode { readInt() } ?: error("Missing field `x`"),
                    y = fields["y"]?.decode { readInt() } ?: error("Missing field `y`"),
                    width = fields["width"]?.decode { readInt() } ?: error("Missing field `width`"),
                    height = fields["height"]?.decode { readInt() } ?: error("Missing field `height`"),
                    isAlwaysOnTop = fields["isAlwaysOnTop"]?.decode { readBoolean() }
                        ?: error("Missing field `isAlwaysOnTop`")
                )
            }
        }

        WindowsState(windowStates)
    }
}
