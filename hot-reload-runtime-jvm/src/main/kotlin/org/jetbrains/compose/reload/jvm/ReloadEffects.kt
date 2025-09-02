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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import org.jetbrains.compose.devtools.api.ReloadState
import org.jetbrains.compose.reload.InternalHotReloadApi
import org.jetbrains.compose.reload.agent.orchestration
import org.jetbrains.compose.reload.core.HotReloadEnvironment.reloadOverlayAnimationsEnabled
import org.jetbrains.compose.reload.core.Try
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.debug
import org.jetbrains.compose.reload.core.error
import org.jetbrains.compose.reload.core.leftOr
import org.jetbrains.compose.reload.orchestration.flowOf
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import java.lang.invoke.MethodHandles


private val logger = createLogger()

private val borderEffect: RuntimeEffect? by lazy { loadRuntimeEffect("shaders/border.glsl") }
private val glitchEffect: RuntimeEffect? by lazy { loadRuntimeEffect("shaders/glitch.glsl") }

private object StatusColors {
    val idle = Color.Transparent
    val ok = Color(0xFF21D789)
    val reloading = Color(0xFFFC801D)
    val error = Color(0xFFFE2857)
}

private object EffectAnimationSpecs {
    val enabled = reloadOverlayAnimationsEnabled
    const val timeStart = 0f
    const val timeEnd = 1f

    const val timeDuration = 2000
    const val fadeDuration = 400
    const val colorDuration = 100

    fun timeAnimation(): TweenSpec<Float> = tween(durationMillis = timeDuration, easing = LinearEasing)
    fun colorAnimation(): TweenSpec<Color> = tween(durationMillis = colorDuration, easing = LinearEasing)
    fun fadeAnimation(): TweenSpec<Color> = tween(durationMillis = fadeDuration, easing = LinearEasing)
}

@Composable
@InternalHotReloadApi
internal fun ReloadEffects(child: @Composable () -> Unit) {
    // flags to only show overlay after everything is initialised
    // mainly required so that we don't show 'green' overlay at the launch of the app
    var initialized by remember { mutableStateOf(false) }

    val reloadState by remember { orchestration.states.flowOf(ReloadState.Key) }.collectAsState(initial = ReloadState.default)
    val color = remember { androidx.compose.animation.Animatable(overlayColor(reloadState, !initialized)) }
    val timeAnim = remember { Animatable(EffectAnimationSpecs.timeStart) }

    logger.debug { "Recomposing ReloadOverlay: $reloadState" }

    // Launch color animations
    LaunchedEffect(reloadState) {
        // update color only on the second iteration
        color.animate(overlayColor(reloadState, !initialized), animationSpec = EffectAnimationSpecs.colorAnimation())
        initialized = true

        if (reloadState is ReloadState.Ok) {
            delay(1000)
            color.animate(StatusColors.idle, animationSpec = EffectAnimationSpecs.fadeAnimation())
        }
    }

    // Launch time animation
    LaunchedEffect(reloadState) {
        timeAnim.snapTo(EffectAnimationSpecs.timeStart)
        if (reloadState is ReloadState.Ok) {
            timeAnim.animate(EffectAnimationSpecs.timeEnd, animationSpec = EffectAnimationSpecs.timeAnimation())
        } else {
            timeAnim.animate(
                EffectAnimationSpecs.timeEnd,
                animationSpec = infiniteRepeatable(
                    animation = EffectAnimationSpecs.timeAnimation(),
                    repeatMode = RepeatMode.Restart
                )
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                if (reloadState !is ReloadState.Failed) return@graphicsLayer
                renderEffect = ImageFilter.makeRuntimeShader(
                    glitchShader(size, timeAnim.value) ?: return@graphicsLayer,
                    shaderNames = arrayOf("content"),
                    inputs = arrayOf(null)
                ).asComposeRenderEffect()
            }
    ) {
        child()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    renderEffect = ImageFilter.makeRuntimeShader(
                        borderShader(size, timeAnim.value, color.value) ?: return@graphicsLayer,
                        shaderNames = arrayOf(),
                        inputs = arrayOf()
                    ).asComposeRenderEffect()
                }
        ) {}
    }
}

private suspend fun Animatable<Float, AnimationVector1D>.animate(
    targetValue: Float,
    animationSpec: AnimationSpec<Float>
) {
    if (!EffectAnimationSpecs.enabled) snapTo(targetValue)
    else animateTo(targetValue, animationSpec = animationSpec)
}

private suspend fun Animatable<Color, AnimationVector4D>.animate(
    targetValue: Color,
    animationSpec: AnimationSpec<Color>
) {
    if (!EffectAnimationSpecs.enabled) snapTo(targetValue)
    else animateTo(targetValue, animationSpec = animationSpec)
}

private fun overlayColor(state: ReloadState, isInitial: Boolean = false): Color {
    return when (state) {
        is ReloadState.Ok -> if (isInitial) StatusColors.idle else StatusColors.ok
        is ReloadState.Reloading -> StatusColors.reloading
        is ReloadState.Failed -> StatusColors.error
    }
}


private fun glitchShader(size: Size, time: Float): RuntimeShaderBuilder? {
    return RuntimeShaderBuilder(glitchEffect ?: return null).apply {
        uniform("iResolution", size.width, size.height)
        uniform("iTime", time)
    }
}

private fun borderShader(size: Size, time: Float, color: Color): RuntimeShaderBuilder? {
    return RuntimeShaderBuilder(borderEffect ?: return null).apply {
        uniform("iResolution", size.width, size.height)
        uniform("iFrequency", 0.5f)
        uniform("iTime", time)
        uniform("iBaseColor", color.red, color.green, color.blue, color.alpha)
    }
}

private fun loadRuntimeEffect(path: String): RuntimeEffect? =
    Try {
        val text = MethodHandles.lookup().lookupClass().classLoader.getResource(path)!!.readText()
        RuntimeEffect.makeForShader(text)
    }.leftOr { e ->
        logger.error("Error loading \"$path\" runtime effect: ", e.value)
        null
    }