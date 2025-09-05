/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.jvm

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Surface
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
import org.jetbrains.compose.reload.agent.orchestration
import org.jetbrains.compose.reload.core.HotReloadEnvironment.glitchOverlayEnabled
import org.jetbrains.compose.reload.core.Try
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.error
import org.jetbrains.compose.reload.core.leftOr
import org.jetbrains.compose.reload.orchestration.flowOf
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import java.lang.invoke.MethodHandles


private val logger = createLogger()

private object StatusColors {
    val idle = Color.Transparent
    val ok = Color(0xFF21D789)
    val reloading = Color(0xFFFC801D)
    val error = Color(0xFFFE2857)
}

private val borderEffect: RuntimeEffect? by lazy { loadRuntimeEffect("shaders/border.glsl") }
private val glitchEffect: RuntimeEffect? by lazy { loadRuntimeEffect("shaders/glitch.glsl") }

// Animation constants
private const val timeAnimationStart = 0f
private const val timeAnimationEnd = 1f

private const val timeAnimationDuration = 2000
private const val fadeAnimationDuration = 400
private const val colorAnimationDuration = 100

@Composable
internal fun ReloadOverlay(child: @Composable () -> Unit) {
    var initialized by remember { mutableStateOf(false) }
    val reloadState by remember { orchestration.states.flowOf(ReloadState.Key) }.collectAsState(initial = ReloadState.default)
    val color = remember { androidx.compose.animation.Animatable(overlayColor(reloadState, !initialized)) }
    var showReloadBorder by remember { mutableStateOf(false) }
    val timeAnim = remember { Animatable(timeAnimationStart) }

    // Launch color animations
    LaunchedEffect(reloadState) {
        // update color only on the second iteration
        color.animateTo(
            overlayColor(reloadState, !initialized),
            animationSpec = tween(durationMillis = colorAnimationDuration, easing = LinearEasing)
        )
        initialized = true
        showReloadBorder = true

        if (reloadState is ReloadState.Ok) {
            delay(1000)
            color.animateTo(
                StatusColors.idle,
                animationSpec = tween(durationMillis = fadeAnimationDuration, easing = LinearEasing)
            )
            showReloadBorder = false
        }
    }

    // Launch time animation
    LaunchedEffect(reloadState) {
        timeAnim.snapTo(timeAnimationStart)
        if (reloadState is ReloadState.Ok) {
            timeAnim.animateTo(timeAnimationEnd, animationSpec = tween(durationMillis = 2000, easing = LinearEasing))
        } else {
            timeAnim.animateTo(
                timeAnimationEnd, animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = timeAnimationDuration, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                )
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                if (!glitchOverlayEnabled) return@graphicsLayer
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
                    if (!showReloadBorder) return@graphicsLayer
                    val shader = borderShader(size, timeAnim.value, color.value) ?: return@graphicsLayer
                    renderEffect = ImageFilter.makeRuntimeShader(
                        runtimeShaderBuilder = shader,
                        shaderNames = arrayOf(),
                        inputs = arrayOf()
                    ).asComposeRenderEffect()
                }
        ) {}
    }
}

/**
 * Use custom [Box] that is basically a fully transparent [Surface], because using
 * an actual [androidx.compose.foundation.layout.Box] will make us vulnerable to ABI changes in Compose layout.
 * Using [Surface] is safer, as it does not expose us directly to Compose layout changes.
 */
@Composable
private fun Box(modifier: Modifier, content: @Composable () -> Unit) {
    Surface(
        color = Color.Transparent,
        contentColor = Color.Transparent,
        modifier = modifier,
        content = content,
    )
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