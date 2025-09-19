/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

@file:OptIn(ExperimentalHotReloadApi::class)

package org.jetbrains.compose.reload.jvm.effects

import androidx.compose.animation.Animatable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.devtools.api.ReloadAnimationSpec
import org.jetbrains.compose.devtools.api.ReloadAnimationSpec.milliseconds
import org.jetbrains.compose.devtools.api.ReloadAnimationSpec.okStatusRetention
import org.jetbrains.compose.devtools.api.ReloadAnimationSpec.statusColorFadeDuration
import org.jetbrains.compose.devtools.api.ReloadColors
import org.jetbrains.compose.devtools.api.ReloadEffect
import org.jetbrains.compose.devtools.api.ReloadState
import org.jetbrains.compose.reload.ExperimentalHotReloadApi
import org.jetbrains.compose.reload.core.Update
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder

internal class BorderGlowEffect : ReloadEffect.OverlayEffect {

    private val borderGlowRuntimeEffect: RuntimeEffect? = loadRuntimeEffect("shaders/glow.glsl")

    @Composable
    override fun effectOverlay(state: ReloadState) {
        val stateChange = rememberChanges(state)

        val targetColor = when (state) {
            is ReloadState.Ok -> ReloadColors.okDarker
            is ReloadState.Reloading -> ReloadColors.reloading
            is ReloadState.Failed -> ReloadColors.error
        }

        var isVisible by remember { mutableStateOf(false) }
        val currentColor = remember { Animatable(targetColor) }
        val currentScale = remember { Animatable(1f) }

        LaunchedEffect(state.javaClass) {
            if (stateChange.previous == null) return@LaunchedEffect

            val isVisibleBefore = isVisible
            isVisible = true

            launch {
                if (!isVisibleBefore) currentScale.snapTo(1f)
                else currentScale.animateTo(1f, spring())
            }

            /* Transition the displayed color */
            launch {
                if (!isVisibleBefore) currentColor.snapTo(targetColor)
                else currentColor.animateTo(
                    targetColor, animationSpec = tween(statusColorFadeDuration.milliseconds)
                )
            }

            /* Display 'pumping' effect when state was OK */
            launch {
                if (state is ReloadState.Ok) {

                    currentScale.animateTo(
                        targetValue = 1.5f,
                        animationSpec = tween(
                            durationMillis = okStatusRetention.milliseconds / 2,
                            easing = FastOutSlowInEasing
                        )
                    )

                    delay(okStatusRetention / 2)

                    isVisible = false
                    currentScale.animateTo(
                        1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
                    )
                }
            }
        }

        AnimatedVisibility(
            isVisible,
            enter = fadeIn(),
            exit = fadeOut(tween(ReloadAnimationSpec.statusFadeoutDuration.milliseconds))
        ) {
            val transition = rememberInfiniteTransition()
            val time by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(2000), RepeatMode.Restart))

            Box(modifier = Modifier.fillMaxSize().graphicsLayer {
                val color = currentColor.value
                val glow = borderGlowRuntimeEffect ?: return@graphicsLayer
                val shader = RuntimeShaderBuilder(glow).apply {
                    uniform("iResolution", size.width, size.height)
                    uniform("iFrequency", 0.5f)
                    uniform("iTime", time)
                    uniform("iScale", currentScale.value * 15)
                    uniform("iBaseColor", color.red, color.green, color.blue, color.alpha)
                }

                renderEffect = ImageFilter.makeRuntimeShader(shader, arrayOf(), arrayOf())
                    .asComposeRenderEffect()
            }) {
                // Empty, we only render the shader here!
            }
        }
    }
}

@Composable
private fun <T> rememberChanges(value: T): Update<T?> {
    val previous = remember { mutableStateOf<T?>(null) }
    val update = Update(previous.value, value)
    previous.value = value
    return update
}
