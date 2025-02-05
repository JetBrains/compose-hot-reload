/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

@file:OptIn(ExperimentalComposeUiApi::class)

package org.jetbrains.compose.reload.jvm.tooling

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ApplicationScope
import io.sellmair.evas.compose.composeValue
import org.jetbrains.compose.reload.jvm.tooling.states.WindowsState

@Composable
fun ApplicationScope.DevOverlays() {
    val windowsState = WindowsState.composeValue()
    windowsState.windows.forEach { (windowId, windowState) ->
        DevToolingSidecar(windowId, windowState, isAlwaysOnTop = windowsState.alwaysOnTop[windowId] == true)
        DevToolingErrorOverlay(windowId, windowState)
    }
}
