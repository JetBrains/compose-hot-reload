/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

@file:OptIn(ExperimentalHotReloadApi::class)

package org.jetbrains.compose.reload.jvm.effects

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import org.jetbrains.compose.devtools.api.ReloadState
import org.jetbrains.compose.reload.ExperimentalHotReloadApi
import org.jetbrains.compose.devtools.api.ReloadEffect
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder

internal class GlitchEffect : ReloadEffect.ModifierEffect {

    private val runtimeEffect: RuntimeEffect? = loadRuntimeEffect("shaders/glitch.glsl")

    @Composable
    override fun effectModifier(state: ReloadState): Modifier {
        if (runtimeEffect == null) return Modifier
        if (state !is ReloadState.Failed) return Modifier

        val transition = rememberInfiniteTransition()
        val time by transition.animateFloat(0f, 10f, infiniteRepeatable(tween(15000, easing = LinearEasing)))

        return Modifier.graphicsLayer {
            val shader = RuntimeShaderBuilder(runtimeEffect).apply {
                uniform("iResolution", size.width, size.height)
                uniform("iTime", time)
            }

            renderEffect = ImageFilter.makeRuntimeShader(
                shader,
                shaderNames = arrayOf("content"),
                inputs = arrayOf(null)
            ).asComposeRenderEffect()
        }
    }
}
