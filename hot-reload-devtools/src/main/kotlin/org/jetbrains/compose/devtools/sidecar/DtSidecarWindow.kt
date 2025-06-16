/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.devtools.sidecar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateContentSize
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.WindowState
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
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ApplicationWindowGainedFocus
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ShutdownRequest
import kotlin.system.exitProcess

private val logger = createLogger()

// Modern rounded corners like JetBrains Toolbox
private val DevToolingSidecarShape = RoundedCornerShape(8.dp)

@Composable
fun DtSidecarWindow(
    windowId: WindowId,
    windowState: WindowState,
    isAlwaysOnTop: Boolean,
) {

    var isExpanded by remember { mutableStateOf(false) }

    DialogWindow(
        onCloseRequest = {
            ShutdownRequest("Requested by user through 'devtools'").sendBlocking()
            exitProcess(0)
        },
        state = DtSidecarWindowState(windowState, isExpanded),
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
                        .animateContentSize(alignment = Alignment.TopCenter)
                    ,
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
