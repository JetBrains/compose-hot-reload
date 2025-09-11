/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.jvm.effects

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import org.jetbrains.compose.devtools.api.ReloadState
import org.jetbrains.compose.reload.InternalHotReloadApi
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder

@InternalHotReloadApi
internal object GlitchOverlayEffect : ReloadOverlayEffect {
    private val glitchEffect: RuntimeEffect? by lazy { loadRuntimeEffect("shaders/glitch.glsl") }

    override fun render(state: ReloadState, size: Size, time: Float, color: Color): RenderEffect? {
        if (state !is ReloadState.Failed) return null
        val glitch = glitchEffect ?: return null
        val shader = RuntimeShaderBuilder(glitch).apply {
            uniform("iResolution", size.width, size.height)
            uniform("iTime", time)
        }
        return ImageFilter.makeRuntimeShader(
            shader,
            shaderNames = arrayOf("content"),
            inputs = arrayOf(null)
        ).asComposeRenderEffect()
    }
}