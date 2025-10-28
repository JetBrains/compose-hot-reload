/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.devtools

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.LocalWindowExceptionHandlerFactory
import androidx.compose.ui.window.WindowExceptionHandler
import androidx.compose.ui.window.application
import io.sellmair.evas.compose.LocalEvents
import io.sellmair.evas.compose.LocalStates
import io.sellmair.evas.compose.composeValue
import io.sellmair.evas.eventsOrThrow
import io.sellmair.evas.statesOrThrow
import org.jetbrains.compose.devtools.errorOverlay.DevToolingErrorOverlay
import org.jetbrains.compose.devtools.sidecar.DtSidecarWindow
import org.jetbrains.compose.devtools.sidecar.DtDetachedSidecarWindow
import org.jetbrains.compose.devtools.states.DtLifecycleState
import org.jetbrains.compose.devtools.states.WindowsUIState
import org.jetbrains.compose.reload.core.HotReloadEnvironment
import org.jetbrains.compose.reload.core.HotReloadEnvironment.devToolsDetached
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.debug
import org.jetbrains.compose.reload.core.error
import org.jetbrains.compose.reload.core.info
import org.jetbrains.compose.reload.orchestration.OrchestrationClientRole
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage

private val logger = createLogger()

@OptIn(ExperimentalComposeUiApi::class)
internal fun startDevToolsUI() {
    if (
        HotReloadEnvironment.isHeadless ||
        HotReloadEnvironment.devToolsIsHeadless ||
        !HotReloadEnvironment.devToolsEnabled
    ) {
        logger.info("DevTools started headless")
        return
    }

    try {
        application(exitProcessOnExit = false) {
            CompositionLocalProvider(
                LocalWindowExceptionHandlerFactory provides { DevToolsWindowExceptionHandler },
                LocalEvents provides (applicationScope.coroutineContext.eventsOrThrow),
                LocalStates provides (applicationScope.coroutineContext.statesOrThrow),
            ) {
                DevToolsUI()
            }
        }
    } catch (t: Throwable) {
        logger.error("Exception when starting DevTools UI", t)
        OrchestrationMessage.CriticalException(OrchestrationClientRole.Tooling, t).sendAsync()
    }
}

@Composable
private fun DevToolsUI() {
    val windowsState = WindowsUIState.composeValue()
    val lifecycleState = DtLifecycleState.composeValue()

    if (!lifecycleState.isActive) {
        logger.info("DevTools UI is shutting down")
        return
    }

    if (devToolsDetached) {
        DtDetachedSidecarWindow()
    }

    logger.debug("Composing '${windowsState.windows.size}' windows")
    windowsState.windows.forEach { (windowId, windowState) ->
        key(windowId) {
            CompositionLocalProvider(targetApplicationWindowStateLocal provides windowState) {
                if (!devToolsDetached) {
                    DtSidecarWindow(
                        windowId, windowState,
                        isAlwaysOnTop = windowsState.alwaysOnTop[windowId] == true
                    )
                }
                DevToolingErrorOverlay(windowId, windowState)
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
private object DevToolsWindowExceptionHandler : WindowExceptionHandler {
    private val logger = createLogger()

    override fun onException(throwable: Throwable) {
        logger.error("Exception when composing window", throwable)
        OrchestrationMessage.CriticalException(OrchestrationClientRole.Tooling, throwable).sendAsync()
    }
}
