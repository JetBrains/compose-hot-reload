/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.devtools.sidecar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import org.jetbrains.compose.devtools.sendBlocking
import org.jetbrains.compose.devtools.theme.DtTitles.COMPOSE_HOT_RELOAD
import org.jetbrains.compose.devtools.theme.DtTitles.COMPOSE_HOT_RELOAD_TITLE
import org.jetbrains.compose.devtools.theme.DtTitles.DEV_TOOLS
import org.jetbrains.compose.devtools.theme.DtPadding
import org.jetbrains.compose.devtools.theme.DtShapes
import org.jetbrains.compose.devtools.theme.DtSizes
import org.jetbrains.compose.devtools.widgets.DtReloadStatusBanner
import org.jetbrains.compose.devtools.widgets.dtBackground
import org.jetbrains.compose.reload.core.WindowId
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ShutdownRequest
import kotlin.system.exitProcess

@Composable
fun DtDetachedSidecarWindow() {
    Window(
        title = COMPOSE_HOT_RELOAD_TITLE,
        onCloseRequest = {
            ShutdownRequest("Requested by user through $DEV_TOOLS").sendBlocking()
            exitProcess(0)
        },
    ) {
        configureTaskbarIcon(window)
        DtDetachedSidecarContent()
    }

    // We need to do this *after* the window is created
    configureTaskbarName()
}

@Composable
fun DtDetachedSidecarContent(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxSize()
            .dtBackground(shape = DtShapes.SquareCornerShape),
        horizontalArrangement = Arrangement.End,
    ) {
        DtDetachedSidecarBody(Modifier.fillMaxSize())
    }
}

@Composable
fun DtDetachedStatusBar(
    windowId: WindowId,
    windowState: WindowState,
    isAlwaysOnTop: Boolean,
) {
    val initialSize = getSideCarWindowSize(windowState.size, false).withStatusBarOffset()
    val initialPosition = getSideCarWindowPosition(windowState.position, initialSize.width)

    DtAnimatedWindow(
        windowId = windowId,
        windowState = windowState,
        initialSize = initialSize,
        initialPosition = initialPosition,
        isExpandedByDefault = false,
        onStateUpdate = { skipAnimation ->
            val newSize = animateWindowSize(windowState.size, false)
            val newPosition = animateWindowPosition(windowState.position, newSize, skipAnimation)
            newSize.withStatusBarOffset() to newPosition.withStatusBarOffset()
        },
        title = "$COMPOSE_HOT_RELOAD Status Bar",
        alwaysOnTop = isAlwaysOnTop,
    ) {
        DtReloadStatusBanner(
            modifier = Modifier
                .padding(DtPadding.small)
        )
    }
}

// permanently set the width of the status bar window to 12.dp
private fun DpSize.withStatusBarOffset() = copy(width = DtPadding.small * 3)

// permanently offset the position of the status bar to compensate for the smaller width
private fun WindowPosition.withStatusBarOffset() = WindowPosition(x + DtSizes.largeLogoSize + DtPadding.small, y)

