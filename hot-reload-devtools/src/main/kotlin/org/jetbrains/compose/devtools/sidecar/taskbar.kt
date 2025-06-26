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
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.WindowScope
import org.jetbrains.compose.devtools.theme.DtTitles.COMPOSE_HOT_RELOAD_TITLE
import org.jetbrains.compose.devtools.theme.composeIcons
import org.jetbrains.compose.devtools.theme.composeLogoPainter
import org.jetbrains.compose.reload.core.Os
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.error
import org.jetbrains.compose.reload.core.info
import org.jetbrains.compose.reload.core.warn
import java.awt.Taskbar
import java.awt.Toolkit
import kotlin.math.log

private val logger = createLogger()

@Composable
internal fun WindowScope.configureTaskbar(): Unit = when (Os.currentOrNull()) {
    Os.Linux -> configureLinuxTaskbar()
    Os.MacOs -> configureMacOsTaskbar()
    Os.Windows -> configureWindowsTaskbar()
    else -> logger.warn("Could not configure $COMPOSE_HOT_RELOAD_TITLE taskbar for unknown OS")
}

@Composable
private fun WindowScope.configureMacOsTaskbar() {
    require(Os.currentOrNull() == Os.MacOs)
    var icon by remember { mutableStateOf<Painter?>(null) }
    val density = LocalDensity.current

    // Set icon
    LaunchedEffect(Unit) {
        if (!Taskbar.isTaskbarSupported()) return@LaunchedEffect
        runCatching {
            icon = composeLogoPainter(density).await()
            Taskbar.getTaskbar().iconImage = icon?.toAwtImage(density, LayoutDirection.Ltr)
        }.onFailure {
            logger.error("Failed loading compose icon", it)
        }
    }
}

@Composable
private fun WindowScope.configureLinuxTaskbar() {
    require(Os.currentOrNull() == Os.Linux)

    // Set icon
    LaunchedEffect(Unit) {
        window.iconImages = composeIcons().await()
    }

    // Set name
    // This is bad, but there is no other way to access the app name in the taskbar
    try {
        val toolkit = Toolkit.getDefaultToolkit()
        val field = toolkit.javaClass.getDeclaredField("awtAppClassName")
        field.isAccessible = true
        field[toolkit] = window.name
    } catch (_: Throwable) {
        logger.info("Could not set dev tools app name in the taskbar")
    }
}

@Composable
private fun WindowScope.configureWindowsTaskbar() {
    require(Os.currentOrNull() == Os.Windows)

    // Set icon
    LaunchedEffect(Unit) {
        window.iconImages = composeIcons().await()
    }

    // Name is automatically inherited from window name
}
