/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.hotReloadUI.widgets

import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@Composable
internal fun TimeAgoText(
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    color: Color = Color.Unspecified
) {
    val duration by rememberStopwatch()
    val text by derivedStateOf { "${duration.displayString()} ago" }

    BasicText(
        text,
        style = TextStyle.Default.copy(fontSize = 10.sp),
        color = ColorProducer { Color.White },
        modifier = modifier,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

private fun Duration.displayString(): String {
    if (toHours() > 0) return "${toHours()} hours"
    if (toMinutes() > 0) return "${toMinutes()} minutes"
    return "${toSeconds()} seconds"
}


/**
 * Returns a state which counts the seconds that have passed
 */
@Composable
private fun rememberStopwatch(): State<Duration> {
    val state = remember { mutableStateOf<Duration>(Duration.ZERO) }

    LaunchedEffect(Unit) {
        val initial = withFrameMillis { frameMillis -> frameMillis }

        while (isActive) {
            delay(128.milliseconds)
            withInfiniteAnimationFrameMillis { frameMillis ->
                val millisecondsPassed = frameMillis - initial
                val newDuration = Duration.ofMillis(millisecondsPassed)
                if (newDuration.seconds != state.value.seconds) {
                    state.value = newDuration
                }
            }
        }
    }

    return state
}
