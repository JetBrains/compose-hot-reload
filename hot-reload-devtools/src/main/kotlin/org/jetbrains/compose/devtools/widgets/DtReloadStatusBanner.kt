/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.compose.devtools.widgets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.sellmair.evas.compose.composeValue
import kotlinx.coroutines.delay
import org.jetbrains.compose.devtools.states.ReloadState
import org.jetbrains.compose.devtools.targetApplicationWindowStateLocal
import org.jetbrains.compose.devtools.theme.DtSizes


@Composable
internal fun DtReloadStatusBanner(modifier: Modifier = Modifier) {
    val state = ReloadState.composeValue()
    val targetApplicationWindowState = targetApplicationWindowStateLocal.current ?: return

    Box(modifier = modifier.heightIn(max = targetApplicationWindowState.size.height).fillMaxHeight()) {
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
            modifier = Modifier.width(DtSizes.small),
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
                        .background(animatedReloadStatusBrush())
                )
            }
        }
    }
}
