/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.test

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.FontHinting
import androidx.compose.ui.text.FontRasterizationSettings
import androidx.compose.ui.text.FontSmoothing
import androidx.compose.ui.text.PlatformParagraphStyle
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.hot_reload_test.generated.resources.Res
import org.jetbrains.compose.hot_reload_test.generated.resources.Roboto_Medium
import org.jetbrains.compose.resources.Font

/**
 * Intended to forcefully create a new Group/Scope.
 */
@Composable
public fun Group(child: @Composable () -> Unit) {
    child()
}

/**
 * Text component which is set up to render as consistent as possible on different platforms.
 */
@OptIn(ExperimentalTextApi::class)
@Composable
public fun TestText(value: String, fontSize: TextUnit = 96.sp) {
    val fontFamily = FontFamily(Font(Res.font.Roboto_Medium, weight = FontWeight.Medium, style = FontStyle.Normal))

    Text(
        text = value,
        fontSize = fontSize,
        fontFamily = fontFamily,
        fontWeight = FontWeight.Normal,
        lineHeight = fontSize,
        style = TextStyle(
            platformStyle = PlatformTextStyle(
                spanStyle = null,
                paragraphStyle = PlatformParagraphStyle(
                    fontRasterizationSettings = FontRasterizationSettings(
                        smoothing = FontSmoothing.AntiAlias,
                        hinting = FontHinting.None,
                        subpixelPositioning = true,
                        autoHintingForced = false
                    )
                )
            )
        )
    )
}
