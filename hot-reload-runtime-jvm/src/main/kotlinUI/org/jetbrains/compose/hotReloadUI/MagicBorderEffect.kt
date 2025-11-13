/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.hotReloadUI

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.compose.devtools.api.ReloadAnimationSpec
import org.jetbrains.compose.devtools.api.ReloadAnimationSpec.statusColorFadeDuration
import org.jetbrains.compose.devtools.api.ReloadEffect
import org.jetbrains.compose.devtools.api.ReloadState

internal class MagicBorderEffect : ReloadEffect.OverlayEffect {

    @Composable
    override fun effectOverlay(state: ReloadState) {
        EffectVisibility(
            visible = state is ReloadState.Reloading
        ) {

            // Animate a phase from 0..1 that loops, used to rotate the gradient around the border
            val infinite = rememberInfiniteTransition(label = "magicBorder")

            val phase by infinite.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 4000, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Restart
                ), label = "magicBorderPhase"
            )

            Spacer(
                Modifier.fillMaxSize().border(
                    border = BorderStroke(3.dp, Brush.sweepGradient(generateColorPalette(phase))),
                    shape = RoundedCornerShape(12.dp)
                )
            )
        }
    }
}

@Composable
private fun EffectVisibility(visible: Boolean, content: @Composable () -> Unit) {
    val transition = remember { Animatable(if (visible) 1f else 0f) }
    val visibilityState = remember { MutableStateFlow(visible) }
    visibilityState.value = visible

    LaunchedEffect(Unit) {
        visibilityState.collect { visible ->
            transition.animateTo(if (visible) 1f else 0f, tween(statusColorFadeDuration.inWholeMilliseconds.toInt()))
        }
    }

    if (transition.value >= 0f) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(transition.value)
        ) {
            content()
        }
    }
}

private fun generateColorPalette(phase: Float): List<Color> {
    // Evenly spaced hues in HSV color space for a classic multi-color gradient
    val stops = 8
    val base = (0 until stops).map { i ->
        val hue = i.toFloat() / stops.toFloat() * 360f
        val shiftedHue = (hue + phase * 360f) % 360f
        // Saturation and value tuned for bright yet not over-saturated look
        Color.hsv(shiftedHue, 0.9f, 1.0f)
    }

    val circular = base + base.first()
    return circular
}
