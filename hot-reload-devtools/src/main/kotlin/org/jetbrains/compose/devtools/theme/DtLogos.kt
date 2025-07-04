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
import java.lang.invoke.MethodHandles
import javax.imageio.ImageIO


private val logger = createLogger()
private val classLoader = MethodHandles.lookup().lookupClass().classLoader

object DtLogos {
    enum class Image(
        private val resource: String
    ) {
        COMPOSE_LOGO("compose-logo"),

        // These logos only have png versions
        // They are required for taskbar icons, which only support png
        COMPOSE_LOGO16("compose-logo16"),
        COMPOSE_LOGO32("compose-logo32"),
        COMPOSE_LOGO48("compose-logo48"),
        COMPOSE_LOGO64("compose-logo64"),
        COMPOSE_LOGO128("compose-logo128"),

        // Build tool logos
        GRADLE_LOGO("gradle-logo"),
        AMPER_LOGO("amper-logo");

        val png: String get() = "img/$resource.png"
        val svg: String get() = "img/$resource.svg"
    }
    val COMPOSE_ICON_PNGS = listOf(
        Image.COMPOSE_LOGO16,
        Image.COMPOSE_LOGO32,
        Image.COMPOSE_LOGO48,
        Image.COMPOSE_LOGO64,
        Image.COMPOSE_LOGO128,
    )

    val composeIcons: Deferred<List<java.awt.Image>> = MainScope().async(Dispatchers.IO) {
        COMPOSE_ICON_PNGS.map {
            classLoader.getResource(it.png)!!.openStream().use { inputStream ->
                ImageIO.read(inputStream)
            }
        }
    }

    fun imageAsPainter(image: Image, density: Density): Deferred<Painter?> = MainScope().async(Dispatchers.IO) {
        runCatching {
            /* Fix for Compose 1.8.0 not rendering svgs correctly on linux */
            if (Os.currentOrNull() != Os.Linux) {
                classLoader.getResource(image.png)!!.openStream()
                    .buffered().use { it.readBytes().decodeToImageBitmap() }.let { BitmapPainter(it) }
            } else {
                classLoader.getResource(image.svg)!!.openStream()
                    .buffered().use { it.readBytes() }.decodeToSvgPainter(density)
            }
        }.getOrElse {
            logger.error("Failed to load image $image")
            null
        }
    }

    fun imageAsPngPainter(image: Image): Deferred<Painter?> = MainScope().async(Dispatchers.IO) {
        runCatching {
            classLoader.getResource(image.png)!!.openStream()
                .buffered().use { it.readBytes().decodeToImageBitmap() }.let { BitmapPainter(it) }
        }.getOrElse {
            logger.error("Failed to load image $image")
            null
        }
    }
}
