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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.devtools.Tag
import org.jetbrains.compose.devtools.tag
import org.jetbrains.compose.devtools.theme.DtLogos
import org.jetbrains.compose.reload.core.BuildSystem

@Composable
fun DtComposeLogo(
    modifier: Modifier = Modifier,
    tint: Color? = Color.White,
) = DtLogo(
    image = DtLogos.Image.COMPOSE_LOGO,
    tint = tint,
    modifier = modifier.tag(Tag.HotReloadLogo)
)

@Composable
fun DtBuildSystemLogo(
    buildTool: BuildSystem,
    modifier: Modifier = Modifier,
    tint: Color? = Color.White,
) {
    val logo = when (buildTool) {
        BuildSystem.Gradle -> DtLogos.Image.GRADLE_LOGO
        BuildSystem.Amper -> DtLogos.Image.AMPER_LOGO
    }
    DtLogo(logo, tint = tint, modifier = modifier.tag(Tag.BuildSystemLogo))
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
            painter, image.name,
            colorFilter = tint?.let { tint -> ColorFilter.tint(tint) },
            modifier = modifier
        )
    }
}
