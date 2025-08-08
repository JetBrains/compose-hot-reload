/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.devtools.sidecar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.LayoutDirection
import org.jetbrains.compose.devtools.theme.DtImages
import org.jetbrains.compose.devtools.theme.DtTitles.COMPOSE_HOT_RELOAD_TITLE
import org.jetbrains.compose.reload.core.Os
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.error
import org.jetbrains.compose.reload.core.info
import org.jetbrains.compose.reload.core.warn
import java.awt.Taskbar
import java.awt.Toolkit

private val logger = createLogger()

@Composable
internal fun configureTaskbarIcon(window: ComposeWindow): Unit = when (Os.currentOrNull()) {
    Os.Linux -> configureLinuxTaskbarIcon(window)
    Os.MacOs -> configureMacOsTaskbarIcon(window)
    Os.Windows -> configureWindowsTaskbarIcon(window)
    else -> logger.warn("Could not configure $COMPOSE_HOT_RELOAD_TITLE taskbar icon, OS ${Os.currentOrNull()} is unknown")
}

@Composable
internal fun configureTaskbarName(): Unit = when (Os.currentOrNull()) {
    Os.Linux -> configureLinuxTaskbarName()
    // MacOS does not allow to set/change the app name in the dock in runtime
    // The only way to do it is to bundle your app
    Os.MacOs -> Unit
    // Windows automatically uses window title as its name in the taskbar
    Os.Windows -> Unit
    else -> logger.warn("Could not configure $COMPOSE_HOT_RELOAD_TITLE taskbar name, OS ${Os.currentOrNull()} is unknown")
}

@Composable
private fun configureMacOsTaskbarIcon(window: ComposeWindow) {
    require(Os.currentOrNull() == Os.MacOs)
    var icon by remember { mutableStateOf<Painter?>(null) }
    val density = LocalDensity.current

    // Set icon
    LaunchedEffect(Unit) {
        if (!Taskbar.isTaskbarSupported()) return@LaunchedEffect
        runCatching {
            icon = DtImages.imageAsPainter(DtImages.Image.COMPOSE_LOGO, density).await()
            Taskbar.getTaskbar().iconImage = icon?.toAwtImage(density, LayoutDirection.Ltr)
        }.onFailure {
            logger.error("Failed loading compose icon", it)
        }
    }
}

@Composable
private fun configureLinuxTaskbarIcon(window: ComposeWindow) {
    require(Os.currentOrNull() == Os.Linux)

    // Set icon
    LaunchedEffect(Unit) {
        window.iconImages = DtImages.composeIcons.await()
    }
}

@Composable
private fun configureWindowsTaskbarIcon(window: ComposeWindow) {
    require(Os.currentOrNull() == Os.Windows)

    LaunchedEffect(Unit) {
        window.iconImages = DtImages.composeIcons.await()
    }
}

@Composable
private fun configureLinuxTaskbarName() {
    require(Os.currentOrNull() == Os.Linux)

    // Set name
    try {
        val toolkit = Toolkit.getDefaultToolkit()
        // This is ugly, but there is no other way to access the app name in the taskbar
        if (toolkit.javaClass.name == "sun.awt.X11.XToolkit") {
            val field = toolkit.javaClass.getDeclaredField("awtAppClassName")
            field.isAccessible = true
            field[toolkit] = COMPOSE_HOT_RELOAD_TITLE
        }
    } catch (_: Throwable) {
        logger.info("Could not set dev tools app name in the taskbar")
    }
}
