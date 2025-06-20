/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.devtools.sidecar

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import kotlinx.coroutines.delay
import org.jetbrains.compose.devtools.invokeWhenMessageReceived
import org.jetbrains.compose.devtools.sendBlocking
import org.jetbrains.compose.devtools.theme.DtPadding
import org.jetbrains.compose.devtools.widgets.DtReloadStatusBanner
import org.jetbrains.compose.reload.core.HotReloadEnvironment.devToolsTransparencyEnabled
import org.jetbrains.compose.reload.core.WindowId
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.debug
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ApplicationWindowGainedFocus
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ShutdownRequest
import java.awt.Window
import kotlin.system.exitProcess

private val logger = createLogger()

@Composable
fun DtDetachedSidecarWindow(
    windowId: WindowId,
    windowState: WindowState,
    isAlwaysOnTop: Boolean,
) {
    var isExpanded by remember { mutableStateOf(true) }

    DtAnimatedWindow(
        windowId,
        windowState,
        isExpandedByDefault = isExpanded,
        onCloseRequest = {
            ShutdownRequest("Requested by user through 'devtools'").sendBlocking()
            exitProcess(0)
        },
        onStateUpdate = {
            animateSizeAndPosition(window, windowState, isExpanded)
        },
        title = "Compose Hot Reload Dev Tools",
        resizable = true,
        alwaysOnTop = isAlwaysOnTop,
    ) {
        invokeWhenMessageReceived<ApplicationWindowGainedFocus> { event ->
            if (event.windowId == windowId) {
                logger.debug("$windowId: Sidecar window 'toFront()'")
                window.toFront()
            }
        }

        DtSidecarWindowContent(
            isExpanded = isExpanded,
            isExpandedChanged = {
                isExpanded = it
            },
            enableStatusBar = false,
            draggableScope = { WindowDraggableArea { it() } },
        )
    }

    if (devToolsTransparencyEnabled) {
        DtDetachedStatusBar(windowId, windowState, isAlwaysOnTop)
    }
}

// permanently set the width of the status bar window to 12.dp
private fun DpSize.withStatusBarOffset() = copy(width = 12.dp)

// permanently offset the position of the status bar to compensate for the smaller width
private fun WindowPosition.withStatusBarOffset() = WindowPosition(x + 32.dp, y)

@Composable
fun DtDetachedStatusBar(
    windowId: WindowId,
    windowState: WindowState,
    isAlwaysOnTop: Boolean,
) {
    val initialSize = getSideCarWindowSize(windowState, false).withStatusBarOffset()
    val initialPosition = getSideCarWindowPosition(windowState, initialSize.width)

    DtAnimatedWindow(
        windowId,
        windowState,
        initialSize = initialSize,
        initialPosition = initialPosition,
        isExpandedByDefault = false,
        onStateUpdate = {
            val newSize = animateWindowSize(windowState, false)
            val newPosition = animateWindowPosition(windowState, newSize)
            newSize.withStatusBarOffset() to newPosition.withStatusBarOffset()
        },
        onCloseRequest = {
            ShutdownRequest("Requested by user through 'devtools'").sendBlocking()
            exitProcess(0)
        },
        title = "Compose Hot Reload Status Bar",
        alwaysOnTop = isAlwaysOnTop,
    ) {
        DtReloadStatusBanner(
            modifier = Modifier
                .padding(DtPadding.small)
        )
    }
}

@Composable
private fun animateSizeAndPosition(
    window: Window,
    windowState: WindowState,
    isExpanded: Boolean
): Pair<DpSize, WindowPosition> {
    var currentIsExpanded by remember { mutableStateOf(isExpanded) }
    var currentSize = window.size.toDpSize()
    var currentPosition = window.location.toWindowPosition()

    if (!currentIsExpanded && isExpanded) {
        currentIsExpanded = true
        currentSize = getSideCarWindowSize(windowState, true)
        currentPosition = WindowPosition(
            currentPosition.x - currentSize.width + window.size.width.dp,
            currentPosition.y
        )
    }

    if (currentIsExpanded && !isExpanded) {
        LaunchedEffect(Unit) {
            delay(animationDuration)
            currentIsExpanded = false
            currentSize = getSideCarWindowSize(windowState, false)
            currentPosition = WindowPosition(
                currentPosition.x + window.size.width.dp - currentSize.width,
                currentPosition.y
            )
            window.size = currentSize.toDimension()
            window.location = currentPosition.toPoint()
        }
    }

    return currentSize to currentPosition
}
