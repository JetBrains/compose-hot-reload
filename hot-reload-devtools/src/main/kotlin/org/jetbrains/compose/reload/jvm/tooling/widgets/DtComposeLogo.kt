/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.jvm.tooling.widgets

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
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.loadImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToSvgPainter
import java.lang.invoke.MethodHandles

private val classLoader = MethodHandles.lookup().lookupClass().classLoader

private val logger = createLogger()

internal val composeLogoSvgBinary = MainScope().async(Dispatchers.IO) {
    classLoader.getResource("img/compose-logo.svg")!!.openStream()
        .buffered().use { input -> input.readBytes() }
}

internal val composeLogoColor = Color(red = 66, green = 133, blue = 244)

@OptIn(ExperimentalResourceApi::class)
@Composable
internal fun DtComposeLogo(
    modifier: Modifier,
    tint: Color? = null
) {
    var painter: Painter? by remember { mutableStateOf<Painter?>(null) }
    val density = LocalDensity.current

    LaunchedEffect(density) {
        withContext(Dispatchers.IO) {
            runCatching {
                painter = composeLogoSvgBinary.await().decodeToSvgPainter(density)
            }.onFailure {
                logger.error("Failed loading compose-logo", it)
            }
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
