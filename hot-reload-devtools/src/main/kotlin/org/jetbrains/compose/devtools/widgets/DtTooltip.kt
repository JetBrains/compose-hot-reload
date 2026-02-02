/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.devtools.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.delay
import org.jetbrains.compose.devtools.sidecar.devToolsUseTransparency
import org.jetbrains.compose.devtools.theme.DtColors
import org.jetbrains.compose.devtools.theme.DtPadding
import org.jetbrains.compose.devtools.theme.DtShapes
import org.jetbrains.compose.devtools.theme.DtTextStyles
import kotlin.time.Duration.Companion.milliseconds

private val tooltipShowDelay = 500.milliseconds
private val tooltipHideDelay = 100.milliseconds

private val defaultTooltipOffset = DpSize(1.dp, 1.dp)

private val tooltipCornerShape = when {
    devToolsUseTransparency -> DtShapes.TooltipCornerShape
    else -> DtShapes.SquareCornerShape
}

@Composable
fun DtTooltip(
    text: String?,
    offset: DpSize = defaultTooltipOffset,
    content: @Composable () -> Unit
) {
    if (text == null) return content()

    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    var isTooltipVisible by remember { mutableStateOf(false) }
    val density = LocalDensity.current

    val windowState = rememberWindowState(
        position = WindowPosition(0.dp, 0.dp),
        size = measureTextWidth(text, style = DtTextStyles.tooltip, density = density),
    )

    LaunchedEffect(isHovered) {
        if (isHovered) {
            delay(tooltipShowDelay)
            isTooltipVisible = true
        } else {
            delay(tooltipHideDelay)
            isTooltipVisible = false
        }
    }

    if (isTooltipVisible) {
        Window(
            onCloseRequest = {},
            state = windowState,
            undecorated = true,
            transparent = devToolsUseTransparency,
            resizable = false,
            visible = true,
            focusable = false,
            alwaysOnTop = true,
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .border(width = 1.dp, color = DtColors.tooltipBorder, shape = tooltipCornerShape)
                    .clip(tooltipCornerShape)
                    .background(DtColors.tooltipBackground)
                    .padding(DtPadding.medium)
                    .fillMaxSize()
            ) {
                DtText(text, style = DtTextStyles.tooltip)
            }
        }
    }

    Box(
        modifier = Modifier
            .hoverable(interactionSource)
            .onGloballyPositioned { coordinates ->
                val topLeft = coordinates.positionOnScreen().toWindowPosition(density)
                val size = coordinates.size.toDpSize(density)
                windowState.position = topLeft + size + offset
            }
    ) {
        content()
    }
}

@Composable
private fun measureTextWidth(
    text: String,
    style: TextStyle,
    density: Density = LocalDensity.current,
): DpSize = with(density) {
    val textMeasurer = rememberTextMeasurer()
    val sizeInPixels = textMeasurer.measure(text, style).size
    return DpSize(
        sizeInPixels.width.toDp() + DtPadding.medium * 2,
        sizeInPixels.height.toDp() + DtPadding.medium * 2,
    )
}

private fun Offset.toWindowPosition(density: Density) = with(density) { WindowPosition(x.toDp(), y.toDp()) }

private fun IntSize.toDpSize(density: Density) = with(density) { DpSize(width.toDp(), height.toDp()) }

private operator fun WindowPosition.plus(other: DpSize): WindowPosition =
    WindowPosition(x + other.width, y + other.height)