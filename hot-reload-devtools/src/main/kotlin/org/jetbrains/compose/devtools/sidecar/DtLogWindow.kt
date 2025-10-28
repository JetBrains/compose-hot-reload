/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.devtools.sidecar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.flow.collectLatest
import org.jetbrains.compose.devtools.Tag
import org.jetbrains.compose.devtools.theme.DtColors
import org.jetbrains.compose.devtools.theme.DtImages
import org.jetbrains.compose.devtools.theme.DtPadding
import org.jetbrains.compose.devtools.theme.DtSizes
import org.jetbrains.compose.devtools.theme.DtTitles
import org.jetbrains.compose.devtools.widgets.DtImage
import org.jetbrains.compose.devtools.widgets.DtIconButton
import org.jetbrains.compose.devtools.widgets.DtWindowController
import org.jetbrains.compose.devtools.widgets.rememberWindowController

@Composable
internal fun DtShowLogsButton() {
    val controller = LocalDtLogWindowController.current

    DtIconButton(
        onClick = { controller.requestFocus() },
        tooltip = "Show logs",
        tag = Tag.ActionButton,
    ) {
        DtImage(
            image = DtImages.Image.LOG_ICON,
            modifier = Modifier.size(DtSizes.iconSize),
            tint = Color.White,
        )
    }

    if (controller.isOpen.value) {
        DtLogWindow { controller.close() }
    }
}

@Composable
fun DtLogWindow(onClose: () -> Unit) {
    val controller = LocalDtLogWindowController.current

    Window(
        onCloseRequest = { onClose() },
        state = rememberWindowState(
            size = DtSizes.defaultWindowSize,
            position = WindowPosition.Aligned(Alignment.Center)
        ),
        title = "${DtTitles.COMPOSE_HOT_RELOAD} Logs"
    ) {
        LaunchedEffect(Unit) {
            controller.focusRequests.collectLatest {
                if (controller.isOpen.value) {
                    window.toFront()
                    window.requestFocus()
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DtColors.applicationBackground)
                .padding(DtPadding.medium)
        ) {
            DtMainConsole(animateBorder = false)
        }
    }
}

val LocalDtLogWindowController = staticCompositionLocalOf<DtWindowController> {
    error("DtNotificationsWindowController not provided")
}

@Composable
fun ProvideLogWindowController(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalDtLogWindowController provides rememberWindowController()) {
        content()
    }
}