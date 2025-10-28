/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.compose.devtools.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object DtTextStyles {
    val default = TextStyle(
        color = DtColors.text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Normal,
        fontFamily = FontFamily.Default,
        letterSpacing = 0.25.sp
    )

    val header1 = default.copy(
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.15.sp
    )

    val header2 = default.copy(
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.1.sp
    )

    val small = default.copy(
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
        color = DtColors.textSecondary
    )

    val code = small.copy(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp
    )

    val smallSemiBold = small.copy(
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = DtColors.text
    )

    val tooltip = default.copy(color = DtColors.tooltipText)
}
