/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.devtools.api

import org.jetbrains.compose.devtools.api.WindowsState.WindowState
import org.jetbrains.compose.reload.core.Try
import org.jetbrains.compose.reload.core.Type
import org.jetbrains.compose.reload.core.WindowId
import org.jetbrains.compose.reload.core.encodeByteArray
import org.jetbrains.compose.reload.core.readString
import org.jetbrains.compose.reload.core.tryDecode
import org.jetbrains.compose.reload.core.type
import org.jetbrains.compose.reload.core.writeString
import org.jetbrains.compose.reload.orchestration.OrchestrationState
import org.jetbrains.compose.reload.orchestration.OrchestrationStateEncoder
import org.jetbrains.compose.reload.orchestration.OrchestrationStateKey
import org.jetbrains.compose.reload.orchestration.stateKey

public data class WindowsState(
    val windows: Map<WindowId, WindowState>
) : OrchestrationState {

    public data class WindowState(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        val isAlwaysOnTop: Boolean
    )

    public companion object {
        public val key: OrchestrationStateKey<WindowsState> = stateKey(WindowsState(emptyMap()))
    }
}

internal class WindowsStateEncoder : OrchestrationStateEncoder<WindowsState> {
    override val type: Type<WindowsState> = type()

    override fun encode(state: WindowsState): ByteArray = encodeByteArray {
        writeInt(state.windows.size)
        state.windows.forEach { (windowId, windowState) ->
            writeString(windowId.value)
            writeInt(windowState.x)
            writeInt(windowState.y)
            writeInt(windowState.width)
            writeInt(windowState.height)
            writeBoolean(windowState.isAlwaysOnTop)
        }
    }

    override fun decode(data: ByteArray): Try<WindowsState> = data.tryDecode {
        val windows = readInt()
        val windowStates = buildMap {
            repeat(windows) {
                this += WindowId(readString()) to WindowState(
                    x = readInt(),
                    y = readInt(),
                    width = readInt(),
                    height = readInt(),
                    isAlwaysOnTop = readBoolean()
                )
            }
        }

        WindowsState(windowStates)
    }
}
