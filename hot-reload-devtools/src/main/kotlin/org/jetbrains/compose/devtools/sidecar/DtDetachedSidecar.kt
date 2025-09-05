/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.devtools.sidecar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Window
import org.jetbrains.compose.devtools.sendBlocking
import org.jetbrains.compose.devtools.theme.DtTitles.COMPOSE_HOT_RELOAD_TITLE
import org.jetbrains.compose.devtools.theme.DtTitles.DEV_TOOLS
import org.jetbrains.compose.devtools.theme.DtShapes
import org.jetbrains.compose.devtools.widgets.dtBackground
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ShutdownRequest
import kotlin.system.exitProcess

@Composable
fun DtDetachedSidecarWindow() {
    Window(
        title = COMPOSE_HOT_RELOAD_TITLE,
        onCloseRequest = {
            ShutdownRequest("Requested by user through $DEV_TOOLS").sendBlocking()
            exitProcess(0)
        },
    ) {
        configureTaskbarIcon(window)
        DtDetachedSidecarContent()
    }

    // We need to do this *after* the window is created
    configureTaskbarName()
}

@Composable
fun DtDetachedSidecarContent(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxSize()
            .dtBackground(shape = DtShapes.SquareCornerShape),
        horizontalArrangement = Arrangement.End,
    ) {
        DtDetachedSidecarBody(Modifier.fillMaxSize())
    }
}
