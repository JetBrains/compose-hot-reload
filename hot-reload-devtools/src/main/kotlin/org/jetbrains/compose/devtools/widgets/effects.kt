/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.devtools.widgets

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale

@Composable
fun Modifier.bouncing(min: Float = 0.95f, max: Float = 1.04f, time: Int = 500): Modifier {
    val transition = rememberInfiniteTransition()
    val scale by transition.animateFloat(
        min, max, infiniteRepeatable(tween(time), RepeatMode.Reverse)
    )

    return scale(scale)
}


@Composable
fun Modifier.shaking(min: Float = -35f, max: Float = 35f, time: Int = 500): Modifier {
    val transition = rememberInfiniteTransition()
    val rotate by transition.animateFloat(
        min, max, infiniteRepeatable(tween(time), RepeatMode.Reverse)
    )

    return this.rotate(rotate)
}
