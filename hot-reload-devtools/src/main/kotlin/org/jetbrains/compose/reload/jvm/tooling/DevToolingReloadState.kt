/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.jvm.tooling

import androidx.compose.animation.Animatable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.sellmair.evas.compose.composeState
import io.sellmair.evas.compose.composeValue
import kotlinx.coroutines.delay
import org.jetbrains.compose.reload.jvm.tooling.states.ReloadState

private val Orange1 = Color(0xFFFC801D)
private val Orange2 = Color(0xFFFDB60D)
private val Green = Color(0xFF3DEA62)
private val Red = Color(0xFFFE2857)

internal val reloadColorOk = Green
internal val reloadColorFailed = Red

@Composable
internal fun ReloadStateBanner(state: ReloadState, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxHeight()) {

        val color by animateReloadStateColor(state)

        var visibilityState by remember { mutableStateOf(false) }
        LaunchedEffect(state) {
            if (state is ReloadState.Ok) {
                delay(1000)
                visibilityState = false
            } else {
                visibilityState = true
            }
        }

        Box(
            modifier = Modifier.width(4.dp),
        ) {
            AnimatedVisibility(
                visible = visibilityState,
                enter = slideInHorizontally(
                    animationSpec = tween(50),
                    initialOffsetX = { it }
                ),
                exit = slideOutHorizontally(
                    animationSpec = tween(200),
                    targetOffsetX = { it }
                ),
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxSize()
                        .clip(RoundedCornerShape(4.dp))
                        .background(animateReloadingIndicatorBrush())
                        .background(color)
                )
            }
        }
    }
}


@Composable
fun Modifier.reloadBorder(
    width: Dp = 1.dp, shape: Shape = RoundedCornerShape(8.dp),
    idleColor: Color = Color.LightGray,
): Modifier {
    val reloadState by ReloadState.composeState()
    val reloadStateColor by animateReloadStateColor(idleColor = idleColor)
    val reloadingIndicatorBrush = animateReloadingIndicatorBrush()

    val border = if (reloadState is ReloadState.Reloading)
        Modifier.border(width, reloadingIndicatorBrush, shape)
    else Modifier.border(width, reloadStateColor, shape)

    return this.then(border)
}

@Composable
fun Modifier.reloadBackground(idleColor: Color): Modifier {
    val reloadStateColor by animateReloadStateColor(idleColor = idleColor)
    return this.background(reloadStateColor.copy(alpha = 0.075f))
}

@Composable
internal fun animateReloadStateColor(
    state: ReloadState = ReloadState.composeValue(),
    idleColor: Color = Color.LightGray,
): State<Color> {
    val color = remember(idleColor) { Animatable(idleColor) }

    LaunchedEffect(state) {
        when (state) {
            is ReloadState.Reloading -> {
                color.snapTo(Color.Transparent)
            }

            is ReloadState.Ok -> {
                color.snapTo(Green)
                delay(1000)
                color.animateTo(idleColor, tween(250))
            }

            is ReloadState.Failed -> {
                color.snapTo(Red)
            }
        }
    }

    return color.asState()
}

@Composable
internal fun animateReloadingIndicatorBrush(): Brush {
    val infiniteTransition = rememberInfiniteTransition()
    val gradientShift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 800f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing))
    )

    return Brush.linearGradient(
        colors = listOf(Orange1, Orange2),
        start = Offset(0f, gradientShift),
        end = Offset(0f, gradientShift + 400),
        tileMode = TileMode.Mirror,
    )
}
