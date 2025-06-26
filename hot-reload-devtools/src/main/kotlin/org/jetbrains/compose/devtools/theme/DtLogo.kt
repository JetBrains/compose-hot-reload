/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.devtools.theme

import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import org.jetbrains.compose.reload.core.Os
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.error
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.decodeToSvgPainter
import java.awt.Image
import java.lang.invoke.MethodHandles
import javax.imageio.ImageIO

internal const val COMPOSE_LOGO_SVG = "img/compose-logo.svg"
internal const val COMPOSE_LOGO_PNG = "img/compose-logo.png"
internal val COMPOSE_ICON_PNGS = listOf(
    "img/compose-logo16.png",
    "img/compose-logo32.png",
    "img/compose-logo48.png",
    "img/compose-logo64.png",
    "img/compose-logo128.png",
)

private val logger = createLogger()
private val classLoader = MethodHandles.lookup().lookupClass().classLoader

internal val composeLogoSvgBinary = MainScope().async(Dispatchers.IO) {
    classLoader.getResource(COMPOSE_LOGO_SVG)!!.openStream()
        .buffered().use { input -> input.readBytes() }
}

/* Fix for Compose 1.8.0 not rendering svgs correctly on linux */
internal val composeLogoPngBinary = MainScope().async(Dispatchers.IO) {
    if (Os.currentOrNull() != Os.Linux) return@async null
    classLoader.getResource(COMPOSE_LOGO_PNG)!!.openStream().use { inputStream ->
        inputStream.readBytes().decodeToImageBitmap()
    }
}

internal fun composeLogoPainter(density: Density): Deferred<Painter?> = MainScope().async(Dispatchers.IO) {
    runCatching {
        composeLogoPngBinary.await()?.let { BitmapPainter(it) }
            ?: composeLogoSvgBinary.await().decodeToSvgPainter(density)
    }.getOrElse {
        logger.error("Failed loading compose-logo", it)
        null
    }
}

internal fun composeIcons(): Deferred<List<Image>> = MainScope().async(Dispatchers.IO) {
    COMPOSE_ICON_PNGS.map {
        classLoader.getResource(COMPOSE_LOGO_PNG)!!.openStream().use { inputStream ->
            ImageIO.read(inputStream)
        }
    }
}
