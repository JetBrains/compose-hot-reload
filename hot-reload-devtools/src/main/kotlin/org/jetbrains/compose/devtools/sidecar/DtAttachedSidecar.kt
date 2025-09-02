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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.window.WindowState
import kotlinx.coroutines.delay
import org.jetbrains.compose.devtools.Tag
import org.jetbrains.compose.devtools.tag
import org.jetbrains.compose.devtools.theme.DtColors
import org.jetbrains.compose.devtools.theme.DtPadding
import org.jetbrains.compose.devtools.theme.DtShapes
import org.jetbrains.compose.devtools.theme.DtSizes
import org.jetbrains.compose.devtools.theme.DtTitles.COMPOSE_HOT_RELOAD_TITLE
import org.jetbrains.compose.devtools.widgets.DtComposeLogo
import org.jetbrains.compose.devtools.widgets.animateReloadStatusColor
import org.jetbrains.compose.devtools.widgets.dtBackground
import org.jetbrains.compose.reload.core.HotReloadEnvironment.devToolsAnimationsEnabled
import org.jetbrains.compose.reload.core.WindowId

private val cornerShape = if (devToolsUseTransparency) DtShapes.RoundedCornerShape else DtShapes.SquareCornerShape
private val useAnimatedTransitions = devToolsUseTransparency && devToolsAnimationsEnabled

@Composable
fun DtAttachedSidecarWindow(
    windowId: WindowId,
    windowState: WindowState,
    isAlwaysOnTop: Boolean,
) {
    var isExpanded by remember { mutableStateOf(false) }

    var isMinimisedVisible by remember { mutableStateOf(true) }
    var minimisedVisibilityChanged by remember { mutableStateOf(false) }
    var isExpandedVisible by remember { mutableStateOf(false) }
    var expandedVisibilityChanged by remember { mutableStateOf(false) }

    LaunchedEffect(isExpanded) {
        if (isExpanded) {
            isMinimisedVisible = false
            isExpandedVisible = true
            minimisedVisibilityChanged = true
            expandedVisibilityChanged = true
        } else {
            delay(animationDuration)
            isMinimisedVisible = true
            minimisedVisibilityChanged = true
            // add delay between the switch so that
            // the minimised window will definitely be visible when expanded disappears
            delay(animationDuration / 5)
            isExpandedVisible = false
            expandedVisibilityChanged = true
        }
    }

    LaunchedEffect(minimisedVisibilityChanged) {
        if (minimisedVisibilityChanged) {
            minimisedVisibilityChanged = false
        }
    }

    LaunchedEffect(expandedVisibilityChanged) {
        if (expandedVisibilityChanged) {
            expandedVisibilityChanged = false
        }
    }

    /**
     * Minimized sidecar window
     */
    DtAnimatedWindow(
        windowId, windowState,
        title = "$COMPOSE_HOT_RELOAD_TITLE (Minimised)",
        alwaysOnTop = isAlwaysOnTop,
        visible = isMinimisedVisible,
        visibilityChanged = minimisedVisibilityChanged,
        isExpandedByDefault = false,
        onStateUpdate = { skipAnimation ->
            val newSize = animateWindowSize(windowState.size, false)
            val newPosition = animateWindowPosition(windowState.position, newSize, skipAnimation)
            newSize to newPosition
        },
    ) {
        DtMinimizedSidecarWindowContent(
            isExpandedChanged = { isExpanded = it }
        )
    }

    /**
     * Expanded sidecar window
     */
    DtAnimatedWindow(
        windowId, windowState,
        title = "$COMPOSE_HOT_RELOAD_TITLE (Expanded)",
        alwaysOnTop = isAlwaysOnTop,
        visible = isExpandedVisible,
        visibilityChanged = expandedVisibilityChanged,
        onStateUpdate = { skipAnimation ->
            val newSize = animateWindowSize(windowState.size, true)
            val newPosition = animateWindowPosition(windowState.position, newSize, skipAnimation)
            newSize to newPosition
        },
        isExpandedByDefault = false,
    ) {
        DtExpandedSidecarWindowContent(
            isExpanded,
            isExpandedChanged = { isExpanded = it }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
internal fun DtMinimizedSidecarWindowContent(
    isExpandedChanged: (isExpanded: Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var inFocus by remember { mutableStateOf(false) }
    Row(
        modifier = modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.End,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .dtBackground(cornerShape)
                .weight(1f, fill = false)
                /* Hack for macOS to save from phantom clicks */
                .onPointerEvent(PointerEventType.Enter) { inFocus = true }
                .onPointerEvent(PointerEventType.Exit) { inFocus = false }
                .tag(Tag.ExpandMinimiseButton)
                .clickable(enabled = inFocus) { isExpandedChanged(true) }
                .padding(DtPadding.small)
                .animateContentSize(alignment = Alignment.TopCenter),
        ) {
            DtComposeLogo(
                Modifier.size(DtSizes.largeLogoSize).padding(DtPadding.small),
                tint = animateReloadStatusColor(
                    idleColor = Color.White,
                ).value
            )
            DtMinimisedReloadCounterStatusItem(
                modifier = Modifier.size(DtSizes.reloadCounterSize),
                showDefaultValue = !devToolsUseTransparency,
            )
        }
    }
}

@Composable
internal fun DtExpandedSidecarWindowContent(
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
                .dtBackground(cornerShape)
                .weight(1f, fill = false),
            transitionSpec = {
                if (useAnimatedTransitions) {
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
                            enter = if (useAnimatedTransitions) fadeIn(tween(220)) else EnterTransition.None,
                            exit = if (useAnimatedTransitions) fadeOut(tween(50)) else ExitTransition.None
                        )
                        .tag(Tag.ExpandMinimiseButton)
                        .clickable { isExpandedChanged(true) }
                        .padding(DtPadding.small)
                        .animateContentSize(alignment = Alignment.TopCenter),
                ) {
                    DtComposeLogo(
                        Modifier.size(DtSizes.largeLogoSize).padding(DtPadding.small),
                        tint = animateReloadStatusColor(
                            idleColor = Color.White,
                            reloadingColor = DtColors.statusColorOrange2
                        ).value
                    )
                    DtMinimisedReloadCounterStatusItem(
                        modifier = Modifier.size(DtSizes.reloadCounterSize),
                        showDefaultValue = !devToolsUseTransparency,
                    )
                }
            } else {
                // Expanded state - show the full UI
                DtAttachedSidecarBody(Modifier.fillMaxSize()) { isExpandedChanged(false) }
            }
        }
    }
}
