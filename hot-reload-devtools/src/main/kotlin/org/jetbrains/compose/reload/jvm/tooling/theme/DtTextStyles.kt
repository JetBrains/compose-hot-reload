/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.compose.reload.jvm.tooling.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object DtTextStyles {
    val default = TextStyle(
        color = DtColors.text,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        fontFamily = FontFamily.Default
    )

    val header1 = default.copy(
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold
    )

    val header2 = default.copy(
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold
    )

    val small = default.copy(
        fontSize = 12.sp,
        fontWeight = FontWeight.Light
    )

    val code = small.copy(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
    )

    val smallSemiBold = small.copy(
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold
    )
}
