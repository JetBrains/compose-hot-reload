/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.jvm

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.AnimationVector4D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import org.jetbrains.compose.devtools.api.ReloadState
import org.jetbrains.compose.reload.InternalHotReloadApi
import org.jetbrains.compose.reload.agent.orchestration
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.debug
import org.jetbrains.compose.reload.jvm.effects.ReloadEffect
import org.jetbrains.compose.reload.jvm.effects.ReloadEffectsConfiguration
import org.jetbrains.compose.reload.jvm.effects.getReloadEffects
import org.jetbrains.compose.reload.jvm.effects.getReloadOverlayEffects
import org.jetbrains.compose.reload.jvm.effects.reloadEffectsConfiguration
import org.jetbrains.compose.reload.orchestration.flowOf
import kotlin.time.Duration


private val logger = createLogger()

@Composable
@InternalHotReloadApi
internal fun ReloadEffects(content: @Composable () -> Unit) = with(ReloadEffectsScope) {
    // flags to only show overlay after everything is initialised
    // mainly required so that we don't show 'green' overlay at the launch of the app
    var initialized by remember { mutableStateOf(false) }

    val state by remember { orchestration.states.flowOf(ReloadState.Key) }.collectAsState(initial = ReloadState.default)
    val color = remember { androidx.compose.animation.Animatable(overlayColor(state, !initialized)) }
    val timeAnim = remember { Animatable(reloadEffectsConfiguration.timeAnimationRange.start) }

    logger.debug { "Recomposing ReloadEffects: $state" }

    // Launch color animations
    LaunchedEffect(state) {
        // update color only on the second iteration
        color.launchColorAnimation(state, isInitial = !initialized)
        initialized = true
    }

    // Launch time animation
    LaunchedEffect(state) {
        timeAnim.launchTimeAnimation(state)
    }

    fun Modifier.withEffects(effects: List<ReloadEffect>): Modifier =
        effects.fold(this.fillMaxSize()) { modifier, effect ->
            modifier.graphicsLayer {
                renderEffect = effect.render(state, size, timeAnim.value, color.value)
            }
        }

    Box(Modifier.withEffects(getReloadOverlayEffects())) {
        content()
        Box(Modifier.withEffects(getReloadEffects()))
    }
}

private object ReloadEffectsScope {
    val configuration: ReloadEffectsConfiguration = reloadEffectsConfiguration

    suspend fun Animatable<Color, AnimationVector4D>.launchColorAnimation(
        state: ReloadState,
        isInitial: Boolean = false,
    ) = with(configuration) {
        animate(overlayColor(state, isInitial), animationSpec = tween(colorAnimationDuration))
        if (state is ReloadState.Ok) {
            delay(fadeDelay)
            animate(idle, animationSpec = tween(fadeAnimationDuration))
        }
    }

    suspend fun Animatable<Float, AnimationVector1D>.launchTimeAnimation(
        state: ReloadState,
    ) = with(configuration) {
        snapTo(timeAnimationRange.start)
        if (state is ReloadState.Ok) {
            animate(
                timeAnimationRange.endInclusive,
                animationSpec = tween(timeAnimationDuration),
            )
        } else {
            animate(
                timeAnimationRange.endInclusive,
                animationSpec = infiniteRepeatable(
                    animation = tween(timeAnimationDuration),
                    repeatMode = RepeatMode.Restart
                ),
            )
        }
    }

    suspend fun Animatable<Float, AnimationVector1D>.animate(
        targetValue: Float,
        animationSpec: AnimationSpec<Float>
    ) {
        if (!configuration.animationsEnabled) snapTo(targetValue)
        else animateTo(targetValue, animationSpec = animationSpec)
    }

    suspend fun Animatable<Color, AnimationVector4D>.animate(
        targetValue: Color,
        animationSpec: AnimationSpec<Color>
    ) {
        if (!configuration.animationsEnabled) snapTo(targetValue)
        else animateTo(targetValue, animationSpec = animationSpec)
    }

    fun <T> tween(duration: Duration): TweenSpec<T> =
        tween(durationMillis = duration.inWholeMilliseconds.toInt(), easing = LinearEasing)

    fun overlayColor(state: ReloadState, isInitial: Boolean = false): Color = when (state) {
        is ReloadState.Ok -> if (isInitial) configuration.idle else configuration.ok
        is ReloadState.Reloading -> configuration.reloading
        is ReloadState.Failed -> configuration.error
    }
}
