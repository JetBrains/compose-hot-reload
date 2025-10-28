/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.compose.devtools.widgets

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.devtools.Tag
import org.jetbrains.compose.devtools.tag
import org.jetbrains.compose.devtools.theme.DtColors
import org.jetbrains.compose.devtools.theme.DtSizes

@Immutable
data class DtButtonState(
    val isHovered: Boolean,
    val isPressed: Boolean,
)

@Composable
fun DtButton(
    onClick: () -> Unit = {},
    tooltip: String? = null,
    modifier: Modifier = Modifier,
    backgroundColor: (Boolean) -> Color = { isHovered -> if (isHovered) DtColors.surfaceActive else DtColors.surface },
    tag: Tag? = null,
    shape: Shape = RoundedCornerShape(6.dp),
    border: BorderStroke? = BorderStroke(0.5.dp, DtColors.border),
    contentModifier: Modifier = Modifier,
    content: @Composable (state: DtButtonState) -> Unit = { _ -> },
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()

    // More subtle animations for a modern feel
    val scale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.96f
            isHovered -> 0.98f
            else -> 1f
        },
        animationSpec = tween(durationMillis = 150)
    )

    val elevation = when {
        isHovered -> 1.dp
        else -> 0.5.dp
    }

    DtTooltip(text = tooltip) {
        Surface(
            modifier = modifier
                .scale(scale)
                .hoverable(interactionSource)
                .defaultMinSize(minWidth = DtSizes.minButtonSize, minHeight = DtSizes.minButtonSize),
            shadowElevation = elevation,
            color = backgroundColor(isHovered),
            shape = shape,
            border = border,
        ) {
            Box(
                modifier = contentModifier
                    .tag(tag)
                    .clickable(interactionSource, ripple(bounded = true), onClick = onClick),
                contentAlignment = Alignment.Center
            ) {
                content(DtButtonState(isHovered = isHovered, isPressed = isPressed))
            }
        }
    }
}
