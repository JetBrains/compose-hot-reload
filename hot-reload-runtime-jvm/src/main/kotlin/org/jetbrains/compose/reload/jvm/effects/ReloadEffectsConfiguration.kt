/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.jvm.effects

import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.reload.InternalHotReloadApi
import org.jetbrains.compose.reload.core.HotReloadEnvironment.reloadOverlayAnimationsEnabled
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@InternalHotReloadApi
internal interface ReloadEffectsConfiguration {
    val animationsEnabled: Boolean get() = reloadOverlayAnimationsEnabled

    val idle: Color
    val ok: Color
    val reloading: Color
    val error: Color

    val timeAnimationRange: ClosedFloatingPointRange<Float>
    val timeAnimationDuration: Duration

    val fadeDelay: Duration
    val fadeAnimationDuration: Duration

    val colorAnimationDuration: Duration
}

@InternalHotReloadApi
internal object DefaultReloadEffectsConfiguration : ReloadEffectsConfiguration {
    override val idle: Color = Color.Transparent
    override val ok: Color = Color(0xFF21D789)
    override val reloading: Color = Color(0xFFFC801D)
    override val error: Color = Color(0xFFFE2857)

    override val timeAnimationRange: ClosedFloatingPointRange<Float> = 0.0f..1.0f
    override val timeAnimationDuration: Duration = 2.seconds

    override val fadeDelay: Duration = 1.seconds
    override val fadeAnimationDuration: Duration = 400.milliseconds

    override val colorAnimationDuration: Duration = 100.milliseconds
}

@InternalHotReloadApi
internal val reloadEffectsConfiguration: ReloadEffectsConfiguration =
    DefaultReloadEffectsConfiguration