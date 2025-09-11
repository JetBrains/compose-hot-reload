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
internal object BorderGlowEffect : ReloadEffect {
    private val glowEffect: RuntimeEffect? by lazy { loadRuntimeEffect("shaders/glow.glsl") }

    override fun render(state: ReloadState, size: Size, time: Float, color: Color): RenderEffect? {
        val glow = glowEffect ?: return null
        val shader = RuntimeShaderBuilder(glow).apply {
            uniform("iResolution", size.width, size.height)
            uniform("iFrequency", 0.5f)
            uniform("iTime", time)
            uniform("iBaseColor", color.red, color.green, color.blue, color.alpha)
        }
        return ImageFilter.makeRuntimeShader(shader, arrayOf(), arrayOf())
            .asComposeRenderEffect()
    }
}