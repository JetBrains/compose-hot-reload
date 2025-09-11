/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.jvm.effects

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import org.jetbrains.compose.devtools.api.ReloadState
import org.jetbrains.compose.reload.InternalHotReloadApi

@InternalHotReloadApi
internal interface ReloadEffect {
    fun render(state: ReloadState, size: Size, time: Float, color: Color): RenderEffect?
}

@InternalHotReloadApi
internal interface ReloadOverlayEffect : ReloadEffect

@InternalHotReloadApi
internal fun getReloadEffects(): List<ReloadEffect> =
    listOf(BorderGlowEffect)

@InternalHotReloadApi
internal fun getReloadOverlayEffects(): List<ReloadOverlayEffect> =
    listOf(GlitchOverlayEffect)