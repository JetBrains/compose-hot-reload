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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.devtools.theme.DtColors
import org.jetbrains.compose.devtools.theme.DtPadding

@Immutable
data class DtButtonState(
    val isHovered: Boolean,
    val isPressed: Boolean,
)

@Composable
fun DtButton(
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = DtPadding.buttonPadding,
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

    // Colors based on state and type
    val backgroundColor = when {
        else -> if (isHovered) DtColors.surfaceActive else DtColors.surface
    }

    val elevation = when {
        isHovered -> 1.dp
        else -> 0.5.dp
    }

    Surface(
        modifier = modifier
            .scale(scale)
            .hoverable(interactionSource)
            .defaultMinSize(minWidth = 24.dp, minHeight = 24.dp),
        shadowElevation = elevation,
        color = backgroundColor,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(0.5.dp, DtColors.border)
    ) {
        Box(
            modifier = Modifier
                .clickable(interactionSource, ripple(bounded = true), onClick = onClick)
                .padding(contentPadding),
            contentAlignment = Alignment.Center
        ) {
            content(DtButtonState(isHovered = isHovered, isPressed = isPressed))
        }
    }
}
