/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.devtools.sidecar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import kotlinx.coroutines.delay
import org.jetbrains.compose.devtools.invokeWhenMessageReceived
import org.jetbrains.compose.devtools.sendBlocking
import org.jetbrains.compose.devtools.theme.DtColors
import org.jetbrains.compose.devtools.theme.DtPadding
import org.jetbrains.compose.devtools.widgets.DtComposeLogo
import org.jetbrains.compose.devtools.widgets.DtReloadStatusBanner
import org.jetbrains.compose.devtools.widgets.animateReloadStatusBackground
import org.jetbrains.compose.devtools.widgets.animateReloadStatusColor
import org.jetbrains.compose.devtools.widgets.animatedReloadStatusBorder
import org.jetbrains.compose.reload.core.HotReloadEnvironment.devToolsTransparencyEnabled
import org.jetbrains.compose.reload.core.WindowId
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.debug
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ApplicationWindowGainedFocus
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ShutdownRequest
import java.awt.Dimension
import java.awt.Point
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.milliseconds

private val logger = createLogger()

// Modern rounded corners like JetBrains Toolbox
private val DevToolingSidecarShape = RoundedCornerShape(8.dp)

// animation time of window effects
private val animationDuration = 512.milliseconds

@Composable
fun DtSidecarWindow(
    windowId: WindowId,
    windowState: WindowState,
    isAlwaysOnTop: Boolean,
) {
    var isExpanded by remember { mutableStateOf(false) }
    var isInitializing by remember { mutableStateOf(true) }

    DialogWindow(
        onCloseRequest = {
            ShutdownRequest("Requested by user through 'devtools'").sendBlocking()
            exitProcess(0)
        },
        undecorated = true,
        transparent = devToolsTransparencyEnabled,
        resizable = false,
        focusable = true,
        alwaysOnTop = isAlwaysOnTop
    ) {
        if (isInitializing) {
            isInitializing = false
            val initialSize = getSideCarWindowSize(windowState, isExpanded)
            window.size = initialSize.toDimension()
            window.location = getSideCarWindowPosition(windowState, initialSize.width).toPoint()
        } else {
            val newSize = animateWindowSize(windowState, isExpanded)
            val newPosition = animateWindowPosition(windowState, newSize)
            if (window.size != newSize.toDimension()) {
                window.size = newSize.toDimension()
            }
            if (window.location != newPosition.toPoint()) {
                window.location = newPosition.toPoint()
            }
        }

        invokeWhenMessageReceived<ApplicationWindowGainedFocus> { event ->
            if (event.windowId == windowId) {
                logger.debug("$windowId: Sidecar window 'toFront()'")
                window.toFront()
            }
        }

        DtSidecarWindowContent(
            isExpanded,
            isExpandedChanged = { isExpanded = it }
        )
    }
}


@Composable
fun DtSidecarWindowContent(
    isExpanded: Boolean = true,
    isExpandedChanged: (isExpanded: Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.End,
    ) {
        AnimatedContent(
            isExpanded,
            modifier = Modifier
                .animatedReloadStatusBorder(
                    shape = DevToolingSidecarShape,
                    idleColor = if (isExpanded) DtColors.border else Color.Transparent
                )
                .clip(DevToolingSidecarShape)
                .background(DtColors.applicationBackground)
                .animateReloadStatusBackground(DtColors.applicationBackground)
                .weight(1f, fill = false),
            transitionSpec = {
                if (devToolsTransparencyEnabled) {
                    (fadeIn(animationSpec = tween(22, delayMillis = 128)) +
                        scaleIn(initialScale = 0.92f, animationSpec = tween(220, delayMillis = 128)))
                        .togetherWith(fadeOut(animationSpec = tween(90)))
                } else EnterTransition.None togetherWith ExitTransition.None
            },
            contentAlignment = Alignment.TopEnd,
        ) { expandedState ->
            if (!expandedState) {
                // Collapsed state - show a modern button with the Compose logo
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .animateEnterExit(
                            enter = if (devToolsTransparencyEnabled) fadeIn(tween(220)) else EnterTransition.None,
                            exit = if (devToolsTransparencyEnabled) fadeOut(tween(50)) else ExitTransition.None
                        ).clickable { isExpandedChanged(true) }
                        .padding(DtPadding.small)
                        .animateContentSize(alignment = Alignment.TopCenter),
                ) {
                    DtComposeLogo(
                        Modifier.size(28.dp).padding(4.dp),
                        tint = animateReloadStatusColor(
                            idleColor = Color.White,
                            reloadingColor = DtColors.statusColorOrange2
                        ).value
                    )
                    DtCollapsedReloadCounterStatusItem()
                }
            } else {
                // Expanded state - show the full UI
                Column {
                    DtSidecarHeaderBar({ isExpandedChanged(false) })
                    DtSidecarBody(Modifier.padding(DtPadding.medium).fillMaxSize())
                }
            }
        }

        if (devToolsTransparencyEnabled) {
            DtReloadStatusBanner(
                modifier = Modifier
                    .padding(DtPadding.small)
            )
        }
    }
}

@Composable
private fun animateWindowSize(
    mainWindowState: WindowState,
    isExpanded: Boolean,
): DpSize {
    val currentIsExpanded = remember { mutableStateOf(isExpanded) }
    var currentSize by remember { mutableStateOf(getSideCarWindowSize(mainWindowState, isExpanded)) }
    val targetSize = getSideCarWindowSize(mainWindowState, isExpanded)
    /* No delay when we do not have the transparency enabled */
    if (!devToolsTransparencyEnabled) {
        currentSize = targetSize
    }

    // We're closing
    if (currentIsExpanded.value && !isExpanded) {
        LaunchedEffect(Unit) {
            delay(animationDuration)
            currentIsExpanded.value = false
            currentSize = targetSize
        }
    }

    // We're opening
    if (!currentIsExpanded.value && isExpanded) {
        currentIsExpanded.value = true
        currentSize = targetSize
    }

    if (currentSize.height != targetSize.height) {
        currentSize = currentSize.copy(height = targetSize.height)
    }
    return currentSize
}

@Composable
private fun animateWindowPosition(
    mainWindowState: WindowState,
    windowSize: DpSize,
): WindowPosition {
    val currentWidth = remember { mutableStateOf(windowSize.width) }
    val targetPosition = getSideCarWindowPosition(mainWindowState, windowSize.width)
    return when {
        currentWidth.value != windowSize.width -> {
            currentWidth.value = windowSize.width
            targetPosition
        }
        else -> {
            val x by animateDpAsState(targetPosition.x, animationSpec = tween(128))
            val y by animateDpAsState(targetPosition.y, animationSpec = tween(128))
            WindowPosition(x, y)
        }
    }
}

private fun DpSize.toDimension(): Dimension = Dimension(width.value.toInt(), height.value.toInt())

private fun WindowPosition.toPoint(): Point = Point(x.value.toInt(), y.value.toInt())

private fun getSideCarWindowPosition(windowState: WindowState, width: Dp): WindowPosition {
    val targetX = windowState.position.x - width - if (!devToolsTransparencyEnabled) 12.dp else 0.dp
    val targetY = windowState.position.y
    return WindowPosition(targetX, targetY)
}

private fun getSideCarWindowSize(windowState: WindowState, isExpanded: Boolean): DpSize {
    return DpSize(
        width = if (isExpanded) 512.dp else 32.dp + 4.dp + (12.dp.takeIf { devToolsTransparencyEnabled } ?: 0.dp),
        height = if (isExpanded) maxOf(windowState.size.height, 512.dp)
        else if (devToolsTransparencyEnabled) maxOf(windowState.size.height, 512.dp) else 28.dp + 4.dp,
    )
}
