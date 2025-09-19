/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.devtools.api

import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.reload.ExperimentalHotReloadApi

/**
 * Default colors for hot reload effects.
 */
@ExperimentalHotReloadApi
public object ReloadColors {
    public val ok: Color = Color(0xFF21D789)
    public val okDarker: Color = Color(0xFF1aab6d)
    public val reloading: Color = Color(0xFFFC801D)
    public val error: Color = Color(0xFFFE2857)
}
