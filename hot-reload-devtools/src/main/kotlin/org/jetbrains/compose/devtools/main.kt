/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

@file:JvmName("Main")

package org.jetbrains.compose.devtools

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
import org.jetbrains.compose.devtools.errorOverlay.DevToolingErrorOverlay
import org.jetbrains.compose.devtools.sidecar.DtAttachedSidecarWindow
import org.jetbrains.compose.devtools.sidecar.DtDetachedSidecarWindow
import org.jetbrains.compose.devtools.sidecar.DtDetachedStatusBar
import org.jetbrains.compose.devtools.sidecar.devToolsUseTransparency
import org.jetbrains.compose.devtools.states.WindowsState
import org.jetbrains.compose.devtools.states.launchBuildSystemState
import org.jetbrains.compose.devtools.states.launchConsoleLogState
import org.jetbrains.compose.devtools.states.launchReloadCountState
import org.jetbrains.compose.devtools.states.launchReloadState
import org.jetbrains.compose.devtools.states.launchUIErrorState
import org.jetbrains.compose.devtools.states.launchWindowsState
import org.jetbrains.compose.reload.core.HotReloadEnvironment
import org.jetbrains.compose.reload.core.HotReloadEnvironment.devToolsDetached
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.info

internal val applicationScope = CoroutineScope(Dispatchers.Main + SupervisorJob() + Events() + States())

private val logger = createLogger()

/**
 * The associated [WindowState] of the target application (aka, the application under hot-reload, which we're
 * providing tooling for
 */
internal val targetApplicationWindowStateLocal = staticCompositionLocalOf<WindowState?> { null }

internal fun CoroutineScope.launchApplicationStates() {
    launchBuildSystemState()
    launchConsoleLogState()
    launchWindowsState()
    launchUIErrorState()
    launchReloadState()
    launchReloadCountState()
}


fun main() {
    logger.info("PID: '${ProcessHandle.current().pid()}'")
    setupDevToolsProcess()

    applicationScope.launchApplicationStates()

    launchRecompiler()

    if (
        HotReloadEnvironment.isHeadless ||
        HotReloadEnvironment.devToolsIsHeadless ||
        !HotReloadEnvironment.devToolsEnabled
    ) {
        logger.info("DevTools started headless")
        return
    }

    application(exitProcessOnExit = false) {
        installEvas(
            applicationScope.coroutineContext.eventsOrThrow,
            applicationScope.coroutineContext.statesOrThrow
        ) {
            val windowsState = WindowsState.composeValue()
            if (devToolsDetached) {
                DtDetachedSidecarWindow()
            }

            logger.info("Composing '${windowsState.windows.size}' windows")
            windowsState.windows.forEach { (windowId, windowState) ->
                key(windowId) {
                    CompositionLocalProvider(targetApplicationWindowStateLocal provides windowState) {
                        if (devToolsDetached) {
                            if (devToolsUseTransparency) {
                                DtDetachedStatusBar(
                                    windowId, windowState,
                                    isAlwaysOnTop = windowsState.alwaysOnTop[windowId] == true
                                )
                            }
                        } else {
                            DtAttachedSidecarWindow(
                                windowId, windowState,
                                isAlwaysOnTop = windowsState.alwaysOnTop[windowId] == true
                            )
                        }
                        DevToolingErrorOverlay(windowId, windowState)
                    }
                }
            }
        }
    }
}
