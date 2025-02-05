/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.jvm.tooling.states

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import io.sellmair.evas.State
import io.sellmair.evas.launchState
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.compose.reload.core.WindowId
import org.jetbrains.compose.reload.jvm.tooling.orchestration
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import org.jetbrains.compose.reload.orchestration.asFlow

data class WindowsState(
    val windows: Map<WindowId, WindowState>,
    val alwaysOnTop: Map<WindowId, Boolean>
) : State {

    companion object Key : State.Key<WindowsState> {
        override val default: WindowsState = WindowsState(windows = emptyMap(), alwaysOnTop = emptyMap())
    }
}

fun CoroutineScope.launchWindowsState() = launchState(WindowsState.Key) {
    val windows = mutableMapOf<WindowId, WindowState>()
    val alwaysOnTop = mutableMapOf<WindowId, Boolean>()

    suspend fun update() {
        WindowsState(windows = windows.toMap(), alwaysOnTop = alwaysOnTop.toMap()).emit()
    }

    orchestration.asFlow().collect { message ->
        if (message is OrchestrationMessage.ApplicationWindowPositioned) {
            windows[message.windowId] = WindowState(
                placement = WindowPlacement.Floating,
                position = WindowPosition(message.x.dp, message.y.dp),
                size = DpSize(message.width.dp, message.height.dp)
            )
            alwaysOnTop[message.windowId] = message.isAlwaysOnTop
            update()
        }

        if (message is OrchestrationMessage.ApplicationWindowGone) {
            windows.remove(message.windowId)
            update()
        }
    }
}
