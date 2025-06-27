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
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowState
import org.jetbrains.compose.devtools.theme.DtColors
import org.jetbrains.compose.devtools.theme.DtPadding
import org.jetbrains.compose.devtools.theme.DtTitles.COMPOSE_HOT_RELOAD_TITLE
import org.jetbrains.compose.devtools.widgets.DtComposeLogo
import org.jetbrains.compose.devtools.widgets.DtReloadStatusBanner
import org.jetbrains.compose.devtools.widgets.animateReloadStatusBackground
import org.jetbrains.compose.devtools.widgets.animateReloadStatusColor
import org.jetbrains.compose.devtools.widgets.animatedReloadStatusBorder
import org.jetbrains.compose.reload.core.HotReloadEnvironment.devToolsTransparencyEnabled
import org.jetbrains.compose.reload.core.WindowId

// Modern rounded corners like JetBrains Toolbox
internal val DevToolingSidecarShape = RoundedCornerShape(8.dp)

@Composable
fun DtAttachedSidecarWindow(
    windowId: WindowId,
    windowState: WindowState,
    isAlwaysOnTop: Boolean,
) {
    var isExpanded by remember { mutableStateOf(false) }
    DtAnimatedWindow(
        windowId,
        windowState,
        isExpandedByDefault = isExpanded,
        onStateUpdate = {
            val newSize = animateWindowSize(windowState.size, isExpanded)
            val newPosition = animateWindowPosition(windowState.position, newSize)
            newSize to newPosition
        },
        title = COMPOSE_HOT_RELOAD_TITLE,
        alwaysOnTop = isAlwaysOnTop,
    ) {
        DtSidecarWindowContent(
            isExpanded = isExpanded,
            isExpandedChanged = { isExpanded = it },
            enableStatusBar = devToolsTransparencyEnabled,
        )
    }
}


@Composable
fun DtSidecarWindowContent(
    isExpanded: Boolean = true,
    isExpandedChanged: (isExpanded: Boolean) -> Unit = {},
    enableStatusBar: Boolean = true,
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
                        )
                        .pointerInput(Unit) {
                            detectDragGestures { change, _ ->
                                change.consume()
                            }
                        }
                        .clickable { isExpandedChanged(true) }
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
                    DtAttachedSidecarHeaderBar({ isExpandedChanged(false) })
                    DtSidecarBody(Modifier.padding(DtPadding.medium).fillMaxSize())
                }
            }
        }

        if (enableStatusBar) {
            DtReloadStatusBanner(
                modifier = Modifier
                    .padding(DtPadding.small)
            )
        }
    }
}
