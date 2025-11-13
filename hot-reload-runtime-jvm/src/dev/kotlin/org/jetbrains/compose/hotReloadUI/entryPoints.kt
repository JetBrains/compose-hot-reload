/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

@file:OptIn(ExperimentalTime::class)

package org.jetbrains.compose.hotReloadUI

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.devtools.api.ReloadState
import org.jetbrains.compose.reload.DevelopmentEntryPoint
import kotlin.time.ExperimentalTime

@Composable
@DevelopmentEntryPoint(windowHeight = 256)
fun ErrorReportOverlayEffectEntryPoint() {
    Box(modifier = Modifier.background(color = Color.White).fillMaxSize()) {
        val overlay = ErrorNotificationOverlayEffect()
        val state = ReloadState.Failed(reason = "Execution failed for task ':widgets:compileKotlinJvm'")
        overlay.effectOverlay(state)
    }
}


@Composable
@DevelopmentEntryPoint
fun MagicBorderEffectEntryPoint() {
    val effect = MagicBorderEffect()
    val state = ReloadState.Reloading()
    Box(modifier = Modifier.background(color = Color.White)) {
        effect.effectOverlay(state)
    }
}
