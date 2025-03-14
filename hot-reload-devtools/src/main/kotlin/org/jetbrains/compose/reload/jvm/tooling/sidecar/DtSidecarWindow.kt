/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.jvm.tooling.sidecar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogState
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import kotlinx.coroutines.delay
import org.jetbrains.compose.reload.core.HotReloadEnvironment.devToolsTransparencyEnabled
import org.jetbrains.compose.reload.core.WindowId
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.jvm.tooling.invokeWhenMessageReceived
import org.jetbrains.compose.reload.jvm.tooling.orchestration
import org.jetbrains.compose.reload.jvm.tooling.theme.DtColors
import org.jetbrains.compose.reload.jvm.tooling.widgets.DtButton
import org.jetbrains.compose.reload.jvm.tooling.widgets.DtComposeLogo
import org.jetbrains.compose.reload.jvm.tooling.widgets.DtReloadStatusBanner
import org.jetbrains.compose.reload.jvm.tooling.widgets.animateReloadStatusBackground
import org.jetbrains.compose.reload.jvm.tooling.widgets.animateReloadStatusColor
import org.jetbrains.compose.reload.jvm.tooling.widgets.animatedReloadStatusBorder
import org.jetbrains.compose.reload.jvm.tooling.widgets.composeLogoColor
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ApplicationWindowGainedFocus
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.milliseconds

private val logger = createLogger()

private val DevToolingSidecarShape = RoundedCornerShape(8.dp)
private val DtButtonSize = 28.dp
private val windowGap = 12.dp // Space between the application window and sidecar window

@Composable
fun DtSidecarWindow(
    windowId: WindowId,
    windowState: WindowState,
    isAlwaysOnTop: Boolean,
) {
    val animationDuration = 512

    var isExpanded by remember { mutableStateOf(false) }
    var sideCarSize by remember { mutableStateOf(getSideCarWindowSize(windowState, isExpanded))}

    if (isExpanded) {
        sideCarSize = getSideCarWindowSize(windowState, isExpanded = true)
    } else {
        LaunchedEffect(Unit) {
            if (devToolsTransparencyEnabled) delay(animationDuration.milliseconds)
            sideCarSize = getSideCarWindowSize(windowState, isExpanded = false)
        }
    }

    val targetX = windowState.position.x - sideCarSize.width - if (!devToolsTransparencyEnabled) windowGap else 0.dp
    val targetY = windowState.position.y

    val xAnimatable = remember { Animatable(targetX.value) }
    val yAnimatable = remember { Animatable(targetY.value) }

    val x by xAnimatable.asState()
    val y by yAnimatable.asState()

    LaunchedEffect(windowState.position) {
        xAnimatable.animateTo(targetX.value)
    }

    LaunchedEffect(windowState.position) {
        yAnimatable.animateTo(targetY.value)
    }

    LaunchedEffect(sideCarSize.width) {
        xAnimatable.snapTo(targetX.value)
        yAnimatable.snapTo(targetY.value)
    }

    val sidecarWindowState = DialogState(
        size = sideCarSize,
        position = WindowPosition(x = x.dp, y = y.dp)
    )

    DialogWindow(
        onCloseRequest = {
            orchestration.sendMessage(OrchestrationMessage.ShutdownRequest()).get()
            exitProcess(0)
        },
        state = sidecarWindowState,
        undecorated = true,
        transparent = devToolsTransparencyEnabled,
        resizable = false,
        focusable = true,
        alwaysOnTop = isAlwaysOnTop
    ) {

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

fun getSideCarWindowSize(windowState: WindowState, isExpanded: Boolean): DpSize {
    return if (devToolsTransparencyEnabled) {
        if (isExpanded) {
            DpSize(512.dp, maxOf(windowState.size.height, 512.dp))
        } else {
            DpSize(DtButtonSize + 12.dp, maxOf(windowState.size.height, 512.dp))
        }
    } else {
        if (isExpanded) {
            DpSize(512.dp, maxOf(windowState.size.height, 512.dp))
        } else {
            DpSize(DtButtonSize, DtButtonSize)
        }
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
            modifier = Modifier.Companion
                .animatedReloadStatusBorder(
                    shape = DevToolingSidecarShape,
                    idleColor = if (isExpanded) Color.LightGray else Color.Transparent
                )
                .clip(DevToolingSidecarShape)
                .background(DtColors.applicationBackground)
                .animateReloadStatusBackground(if (isExpanded) Color.LightGray else DtColors.statusColorOk)
                .weight(1f, fill = false),
            transitionSpec = {
                if (devToolsTransparencyEnabled) {
                    (fadeIn(animationSpec = tween(220, delayMillis = 128)) +
                        scaleIn(initialScale = 0.92f, animationSpec = tween(220, delayMillis = 128)))
                        .togetherWith(fadeOut(animationSpec = tween(90)))
                } else EnterTransition.None togetherWith ExitTransition.None
            },
            contentAlignment = Alignment.TopEnd,
        ) { expandedState ->
            if (!expandedState) {
                DtButton(
                    onClick = { isExpandedChanged(true) },
                    modifier = Modifier.animateEnterExit(
                        enter = if (devToolsTransparencyEnabled) fadeIn(tween(220)) else EnterTransition.None,
                        exit = if (devToolsTransparencyEnabled) fadeOut(tween(50)) else ExitTransition.None
                    ),
                ) {
                    DtComposeLogo(
                        Modifier.size(DtButtonSize).padding(4.dp),
                        tint = animateReloadStatusColor(
                            idleColor = composeLogoColor,
                            reloadingColor = DtColors.statusColorOrange2
                        ).value
                    )
                }

            } else {
                Column {
                    DtSidecarHeaderBar({ isExpandedChanged(false) })
                    DtSidecarBody(Modifier.padding(8.dp).fillMaxSize())
                }
            }
        }

        if (devToolsTransparencyEnabled) {
            DtReloadStatusBanner(
                modifier = Modifier
                    .padding(4.dp)
            )
        }
    }
}
