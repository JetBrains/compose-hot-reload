/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

@file:JvmName("Main")

package org.jetbrains.compose.reload.jvm.tooling

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import io.sellmair.evas.Events
import io.sellmair.evas.States
import io.sellmair.evas.compose.composeValue
import io.sellmair.evas.compose.installEvas
import io.sellmair.evas.eventsOrThrow
import io.sellmair.evas.statesOrThrow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.jetbrains.compose.reload.jvm.tooling.errorOverlay.DevToolingErrorOverlay
import org.jetbrains.compose.reload.jvm.tooling.sidecar.DtSidecarWindow
import org.jetbrains.compose.reload.jvm.tooling.states.WindowsState
import org.jetbrains.compose.reload.jvm.tooling.states.launchConsoleLogState
import org.jetbrains.compose.reload.jvm.tooling.states.launchReloadCountState
import org.jetbrains.compose.reload.jvm.tooling.states.launchReloadState
import org.jetbrains.compose.reload.jvm.tooling.states.launchUIErrorState
import org.jetbrains.compose.reload.jvm.tooling.states.launchWindowsState

internal val applicationScope = CoroutineScope(Dispatchers.Main + SupervisorJob() + Events() + States())

/**
 * The associated [WindowState] of the target application (aka, the application under hot-reload, which we're
 * providing tooling for
 */
internal val targetApplicationWindowStateLocal = staticCompositionLocalOf<WindowState?> { null }

internal fun CoroutineScope.launchApplicationStates() {
    launchConsoleLogState()
    launchWindowsState()
    launchUIErrorState()
    launchReloadState()
    launchReloadCountState()
}


fun main() {
    applicationScope.launchApplicationStates()

    application {
        installEvas(
            applicationScope.coroutineContext.eventsOrThrow,
            applicationScope.coroutineContext.statesOrThrow
        ) {
            val windowsState = WindowsState.composeValue()
            windowsState.windows.forEach { (windowId, windowState) ->
                key(windowId) {
                    CompositionLocalProvider(targetApplicationWindowStateLocal provides windowState) {
                        DtSidecarWindow(
                            windowId, windowState, isAlwaysOnTop = windowsState.alwaysOnTop[windowId] == true
                        )
                        DevToolingErrorOverlay(windowId, windowState)
                    }
                }
            }
        }
    }
}
