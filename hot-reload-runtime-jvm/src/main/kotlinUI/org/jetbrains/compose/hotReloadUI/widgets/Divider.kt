/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.hotReloadUI.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
internal fun Divider(
    modifier: Modifier = Modifier,
    color: Color = Color.White.copy(alpha = DividerAlpha),
    thickness: Dp = 1.dp,
    startIndent: Dp = 0.dp
) {
    val indent = if (startIndent.value != 0f) Modifier.padding(start = startIndent)
    else Modifier

    val targetThickness = if (thickness == Dp.Hairline)
        (1f / LocalDensity.current.density).dp else thickness

    Box(modifier.then(indent).fillMaxWidth().height(targetThickness).background(color = color))
}

private const val DividerAlpha = 0.12f
