/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.devtools.widgets

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.devtools.theme.DtLogos
import org.jetbrains.compose.reload.core.BuildSystem
import org.jetbrains.compose.reload.core.createLogger

@Composable
fun DtComposeLogo(
    modifier: Modifier = Modifier,
    tint: Color? = Color.White,
) = DtLogo(
    image = DtLogos.Image.COMPOSE_LOGO,
    tint = tint,
    modifier = modifier
)

@Composable
fun DtBuildSystemLogo(
    buildTool: BuildSystem?,
    modifier: Modifier = Modifier,
    tint: Color? = Color.White,
) {
    when (buildTool) {
        BuildSystem.Gradle -> DtLogo(DtLogos.Image.GRADLE_LOGO, tint = tint, modifier = modifier)
        BuildSystem.Amper -> DtLogo(DtLogos.Image.AMPER_LOGO, tint = tint, modifier = modifier)
        null -> { /* nothing */ }
    }
}

@Composable
fun DtLogo(
    image: DtLogos.Image,
    tint: Color? = Color.White,
    modifier: Modifier = Modifier,
) {
    var painter: Painter? by remember { mutableStateOf<Painter?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            painter = DtLogos.imageAsPngPainter(image).await()
        }
    }

    painter?.let { painter ->
        Image(
            painter, "Compose Logo",
            colorFilter = tint?.let { tint -> ColorFilter.tint(tint) },
            modifier = modifier
        )
    }
}
