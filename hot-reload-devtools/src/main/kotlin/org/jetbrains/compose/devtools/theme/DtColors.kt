/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

@file:OptIn(ExperimentalHotReloadApi::class)

package org.jetbrains.compose.devtools.theme

import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.devtools.api.ReloadColors
import org.jetbrains.compose.reload.ExperimentalHotReloadApi

object DtColors {

    // UI colors
    val applicationBackground = Color(0xFF2B2D30) // Dark background like JetBrains Toolbox
    val defaultActive = Color(0xFF3C3F41) // Slightly lighter for active elements

    val surface = Color(0xFF3C3F41) // Darker surface for cards and panels
    val surfaceActive = Color(0xFF4E5254) // Slightly lighter for active elements

    val statusColorOk = ReloadColors.ok
    val statusColorOrange1 = Color(0xFFFFCB6B)
    val statusColorOrange2 = ReloadColors.reloading //jetBrainsOrange
    val statusColorError = ReloadColors.error //jetBrainsRed

    val text = Color.White
    val textSecondary = Color(0xFFBBBBBB)
    val border = Color(0xFF5E6060)

    val scrollbar = Color(0xFF898989)

    // Tooltip colors
    val tooltipBackground = applicationBackground
    val tooltipBorder = border
    val tooltipText = Color.White
}
