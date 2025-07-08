/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.compose.devtools.widgets

import androidx.compose.animation.Animatable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.sellmair.evas.compose.composeFlow
import io.sellmair.evas.compose.composeValue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flow
import org.jetbrains.compose.devtools.states.ReloadState
import org.jetbrains.compose.devtools.theme.DtColors
import org.jetbrains.compose.devtools.theme.DtShapes
import org.jetbrains.compose.reload.core.Update
import kotlin.time.Duration.Companion.seconds

@Composable
fun animateReloadStatusColor(
    idleColor: Color = Color.LightGray,
    reloadingColor: Color = DtColors.statusColorOrange2,
    okColor: Color = DtColors.statusColorOk,
    errorColor: Color = DtColors.statusColorError,
): State<Color> {
    val color = remember { Animatable(idleColor) }
    val state = ReloadState.composeFlow()

    LaunchedEffect(idleColor, reloadingColor, okColor, errorColor) {
        state.changes().collectLatest { (_, state) ->
            when (state) {
                is ReloadState.Reloading -> {
                    color.animateTo(reloadingColor)
                }

                is ReloadState.Failed -> {
                    color.animateTo(errorColor)
                }

                is ReloadState.Ok -> {
                    color.animateTo(okColor)
                    delay(1.seconds)
                    color.animateTo(idleColor)
                }
            }
        }
    }

    return color.asState()
}

@OptIn(ExperimentalCoroutinesApi::class)
@Composable
fun animatedReloadStatusBrush(
    okColor: Color = DtColors.statusColorOk,
    errorColor: Color = DtColors.statusColorError,
    idleColor: Color = Color.LightGray,
): Brush {
    val state = ReloadState.composeValue()
    var isIdle by remember { mutableStateOf(true) }

    LaunchedEffect(state) {
        if (state is ReloadState.Ok) {
            delay(1.seconds)
            isIdle = true
        } else {
            isIdle = false
        }
    }

    val movingColorA by animateColorAsState(
        when (state) {
            is ReloadState.Ok -> if (isIdle) idleColor else okColor
            is ReloadState.Failed -> errorColor
            is ReloadState.Reloading -> DtColors.statusColorOrange1
        }
    )

    val movingColorB by animateColorAsState(
        when (state) {
            is ReloadState.Ok -> if (isIdle) idleColor else okColor
            is ReloadState.Failed -> errorColor
            is ReloadState.Reloading -> DtColors.statusColorOrange2
        }
    )

    val movingTransition = rememberInfiniteTransition()
    val movingGradientShift by movingTransition.animateFloat(
        initialValue = 0f,
        targetValue = 800f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing))
    )
    return Brush.linearGradient(
        colors = listOf(movingColorA, movingColorB),
        start = Offset(0f, movingGradientShift),
        end = Offset(0f, movingGradientShift + 400),
        tileMode = TileMode.Mirror,
    )
}

@Composable
fun Modifier.animateReloadStatusBackground(idleColor: Color): Modifier {
    val reloadStateColor by animateReloadStatusColor(idleColor = idleColor)
    return this.background(reloadStateColor.copy(alpha = 0.1f))
}

@Composable
fun Modifier.animatedReloadStatusBorder(
    width: Dp = 1.dp, shape: Shape = DtShapes.RoundedCornerShape,
    idleColor: Color = Color.LightGray,
): Modifier {
    return border(
        width = width,
        brush = animatedReloadStatusBrush(idleColor = idleColor),
        shape = shape
    )
}

private fun <T> Flow<T>.changes(): Flow<Update<T>> = flow<Update<T>> {
    val NULL = Any()
    var previous: Any? = NULL
    collect { new ->
        run emit@{
            @Suppress("UNCHECKED_CAST")
            emit(Update(if (previous != NULL) previous as T else return@emit, new))
        }
        previous = new
    }
}
