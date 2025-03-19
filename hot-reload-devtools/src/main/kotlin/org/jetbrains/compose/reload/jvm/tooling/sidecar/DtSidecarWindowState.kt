/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.jvm.tooling.sidecar

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogState
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import kotlinx.coroutines.delay
import org.jetbrains.compose.reload.core.HotReloadEnvironment.devToolsTransparencyEnabled
import kotlin.time.Duration.Companion.milliseconds

private val animationDuration = 512.milliseconds

@Composable
fun DtSidecarWindowState(
    targetWindowState: WindowState,
    isExpanded: Boolean
): DialogState {
    val state = remember { DialogState() }
    val size = windowSize(targetWindowState, isExpanded)
    val position = animateWindowPosition(size.width, targetWindowState)

    if (state.size != size) {
        state.size = size
    }

    if (state.position != position) {
        state.position = position
    }

    return state
}

@Composable
private fun windowSize(targetWindowState: WindowState, isExpanded: Boolean): DpSize {
    val currentIsExpanded = remember { mutableStateOf(isExpanded) }
    val currentSize = remember { mutableStateOf(getSideCarWindowSize(targetWindowState, isExpanded)) }
    val targetSize = getSideCarWindowSize(targetWindowState, isExpanded)

    /* No delay when we do not have the transparency enabled */
    if (!devToolsTransparencyEnabled) {
        currentSize.value = targetSize
    }

    // We're closing
    if (currentIsExpanded.value && !isExpanded) {
        LaunchedEffect(Unit) {
            delay(animationDuration)
            currentIsExpanded.value = false
            currentSize.value = getSideCarWindowSize(targetWindowState, isExpanded)
        }
    }

    // We're opening
    if (!currentIsExpanded.value && isExpanded) {
        currentIsExpanded.value = true
        currentSize.value = targetSize
    }

    if (currentSize.value.height != targetSize.height) {
        currentSize.value = currentSize.value.copy(height = targetSize.height)
    }

    return currentSize.value
}

@Composable
private fun animateWindowPosition(
    width: Dp,
    targetWindowState: WindowState,
): WindowPosition {
    val targetX = targetWindowState.position.x - width - if (!devToolsTransparencyEnabled) 12.dp else 0.dp
    val targetY = targetWindowState.position.y

    /* Width has changed: Animation shall be skipped */
    val currentWidth = remember { mutableStateOf(width) }
    if (currentWidth.value != width) {
        currentWidth.value = width
        return WindowPosition(targetX, targetY)
    }

    val x by animateDpAsState(targetX, animationSpec = tween(128))
    val y by animateDpAsState(targetY, animationSpec = tween(128))
    return WindowPosition(x, y)
}

private fun getSideCarWindowSize(windowState: WindowState, isExpanded: Boolean): DpSize {
    return DpSize(
        width = if (isExpanded) 512.dp else 28.dp + (12.dp.takeIf { devToolsTransparencyEnabled } ?: 0.dp),
        height = if (isExpanded) maxOf(windowState.size.height, 512.dp)
        else if (devToolsTransparencyEnabled) maxOf(windowState.size.height, 512.dp) else 28.dp,
    )
}
