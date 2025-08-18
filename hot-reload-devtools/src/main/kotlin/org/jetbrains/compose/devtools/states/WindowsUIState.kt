/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.devtools.states

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import io.sellmair.evas.State
import io.sellmair.evas.launchState
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.compose.devtools.api.WindowsState
import org.jetbrains.compose.devtools.orchestration
import org.jetbrains.compose.reload.core.WindowId


data class WindowsUIState(
    val windows: Map<WindowId, WindowState>,
    val alwaysOnTop: Map<WindowId, Boolean>
) : State {

    companion object Key : State.Key<WindowsUIState> {
        override val default: WindowsUIState = WindowsUIState(windows = emptyMap(), alwaysOnTop = emptyMap())
    }
}

fun CoroutineScope.launchWindowsUIState() = launchState(WindowsUIState.Key) {
    /* Cache for the 'WindowState' instances to avoid unnecessary recompositions at the root level */
    val targetWindowStates = hashMapOf<WindowId, WindowState>()

    fun getTargetWindowState(windowId: WindowId, state: WindowsState.WindowState): WindowState {
        val target = targetWindowStates.getOrPut(windowId) {
            WindowState(
                placement = WindowPlacement.Floating,
                isMinimized = false,
                position = WindowPosition(state.x.dp, state.y.dp),
                size = DpSize(state.width.dp, state.height.dp)
            )
        }

        target.placement = WindowPlacement.Floating
        target.size = DpSize(state.width.dp, height = state.height.dp)
        target.position = WindowPosition(state.x.dp, state.y.dp)
        return target
    }

    orchestration.states.get(WindowsState).collect { state ->
        WindowsUIState(
            windows = state.windows.mapValues { (windowId, windowState) ->
                getTargetWindowState(windowId, windowState)
            },
            alwaysOnTop = state.windows.mapValues { (_, windowState) -> windowState.isAlwaysOnTop }
        ).emit()
    }
}
