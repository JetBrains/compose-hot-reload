/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.devtools.sidecar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import org.jetbrains.compose.devtools.sendBlocking
import org.jetbrains.compose.devtools.theme.DtTitles.COMPOSE_HOT_RELOAD
import org.jetbrains.compose.devtools.theme.DtTitles.COMPOSE_HOT_RELOAD_TITLE
import org.jetbrains.compose.devtools.theme.DtTitles.DEV_TOOLS
import org.jetbrains.compose.devtools.theme.DtColors
import org.jetbrains.compose.devtools.theme.DtPadding
import org.jetbrains.compose.devtools.theme.composeLogoPainter
import org.jetbrains.compose.devtools.widgets.DtReloadStatusBanner
import org.jetbrains.compose.devtools.widgets.animateReloadStatusBackground
import org.jetbrains.compose.devtools.widgets.animatedReloadStatusBorder
import org.jetbrains.compose.reload.core.WindowId
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.error
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ShutdownRequest
import java.awt.Taskbar
import kotlin.system.exitProcess

private val logger = createLogger()

@Composable
fun DtDetachedSidecarWindow() {
    val defaultSize = DpSize(500.dp, 600.dp)
    var icon by remember { mutableStateOf<Painter?>(null) }
    val density = LocalDensity.current

    // Mac OS
    LaunchedEffect(Unit) {
        if (!Taskbar.isTaskbarSupported()) return@LaunchedEffect
        runCatching {
            icon = composeLogoPainter(density).await()
            Taskbar.getTaskbar().iconImage = icon?.toAwtImage(density, LayoutDirection.Ltr)
        }.onFailure {
            logger.error("Failed loading compose icon", it)
        }
    }

    Window(
        title = COMPOSE_HOT_RELOAD_TITLE,
        // Windows
        icon = icon,
        onCloseRequest = {
            ShutdownRequest("Requested by user through $DEV_TOOLS").sendBlocking()
            exitProcess(0)
        },
    ) {
        window.minimumSize = defaultSize.toDimension()
        DtDetachedSidecarContent()
    }
}

@Composable
fun DtDetachedSidecarContent(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxSize()
            .animatedReloadStatusBorder(
                shape = DevToolingSidecarShape,
                idleColor = DtColors.border
            )
            .clip(DevToolingSidecarShape)
            .background(DtColors.applicationBackground)
            .animateReloadStatusBackground(DtColors.applicationBackground),
        horizontalArrangement = Arrangement.End,
    ) {
        Column {
            DtDetachedSidecarHeaderBar()
            DtSidecarBody(Modifier.padding(DtPadding.medium).fillMaxSize())
        }
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
        onStateUpdate = {
            val newSize = animateWindowSize(windowState.size, false)
            val newPosition = animateWindowPosition(windowState.position, newSize)
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
private fun DpSize.withStatusBarOffset() = copy(width = 12.dp)

// permanently offset the position of the status bar to compensate for the smaller width
private fun WindowPosition.withStatusBarOffset() = WindowPosition(x + 32.dp, y)

