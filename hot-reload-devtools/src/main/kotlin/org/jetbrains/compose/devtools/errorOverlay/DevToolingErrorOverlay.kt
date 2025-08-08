/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

@file:OptIn(ExperimentalComposeUiApi::class)

package org.jetbrains.compose.devtools.errorOverlay


import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import io.sellmair.evas.compose.composeFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import org.jetbrains.compose.devtools.Tag
import org.jetbrains.compose.devtools.sendAsync
import org.jetbrains.compose.devtools.sidecar.DtConsole
import org.jetbrains.compose.devtools.sidecar.devToolsUseTransparency
import org.jetbrains.compose.devtools.states.UIErrorDescription
import org.jetbrains.compose.devtools.states.UIErrorState
import org.jetbrains.compose.devtools.tag
import org.jetbrains.compose.devtools.theme.DtColors
import org.jetbrains.compose.devtools.theme.DtImages
import org.jetbrains.compose.devtools.theme.DtPadding
import org.jetbrains.compose.devtools.theme.DtShapes
import org.jetbrains.compose.devtools.theme.DtTextStyles
import org.jetbrains.compose.devtools.widgets.DtCopyToClipboardButton
import org.jetbrains.compose.devtools.widgets.DtHeader1
import org.jetbrains.compose.devtools.widgets.DtText
import org.jetbrains.compose.devtools.widgets.DtTextButton
import org.jetbrains.compose.devtools.widgets.dtBorder
import org.jetbrains.compose.devtools.widgets.restartAction
import org.jetbrains.compose.reload.core.WindowId
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ShutdownRequest

@Composable
internal fun ApplicationScope.DevToolingErrorOverlay(windowId: WindowId, windowState: WindowState) {
    val uiExceptionState by UIErrorState.composeFlow()
        .map { value -> value.errors[windowId] }
        .collectAsState(initial = null)

    uiExceptionState?.let { error ->
        var showWindow by remember { mutableStateOf(true) }
        if (!showWindow) return@let

        Window(
            onCloseRequest = {},
            state = windowState,
            undecorated = true,
            transparent = devToolsUseTransparency,
            resizable = false,
            focusable = false,
            alwaysOnTop = true,
        ) {
            LaunchedEffect(Unit) {
                while (true) {
                    window.toFront()
                    delay(128)
                }
            }
            DevToolingErrorOverlay(error, windowState.size) { showWindow = false }
        }
    }
}

@Composable
private fun DevToolingErrorOverlay(
    error: UIErrorDescription,
    size: DpSize,
    closeAction: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(DtShapes.RoundedCornerShape)
            .background(Color.Black.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = DtPadding.smallElementPadding,
                    end = DtPadding.smallElementPadding,
                    bottom = DtPadding.smallElementPadding
                )
        ) {
            Spacer(modifier = Modifier.size(width = size.width, height = size.height * 0.4f))

            Column(
                modifier = Modifier
                    .clip(DtShapes.RoundedCornerShape)
                    .dtBorder(idleColor = DtColors.statusColorError)
                    .background(
                        DtColors.statusColorError.copy(alpha = 0.1f).compositeOver(DtColors.applicationBackground)
                    )
                    .padding(DtPadding.borderPadding),
                verticalArrangement = Arrangement.spacedBy(DtPadding.smallElementPadding),
            ) {

                Row(
                    horizontalArrangement = Arrangement.spacedBy(DtPadding.smallElementPadding),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DtHeader1("UI exception")

                    Spacer(Modifier.weight(1f))

                    DtTextButton(
                        text = "Restart",
                        icon = DtImages.Image.RESTART_ICON,
                        tag = Tag.ActionButton,
                        onClick = restartAction()
                    )

                    DtTextButton(
                        text = "Shutdown",
                        icon = DtImages.Image.CLOSE_ICON,
                        tag = Tag.ActionButton,
                        onClick = { ShutdownRequest("Requested by user through 'devtools'").sendAsync() },
                    )

                    DtTextButton(
                        text = "Close",
                        icon = DtImages.Image.CLOSE_ICON,
                        tag = Tag.ActionButton,
                        onClick = closeAction,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(DtPadding.smallElementPadding)) {
                    DtText(
                        error.message.orEmpty(),
                        modifier = Modifier.tag(Tag.RuntimeErrorText)
                            .widthIn(max = size.width * 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = DtTextStyles.code.copy(
                            textDecoration = TextDecoration.Underline,
                        )
                    )
                    Spacer(Modifier.weight(1f))
                    DtCopyToClipboardButton {
                        "${error.message.orEmpty()}\n" +
                            error.stacktrace.joinToString("\n")
                    }
                }

                DtConsole(
                    logs = error.stacktrace.map { it.toString() },
                    modifier = Modifier.fillMaxSize(),
                    scrollToBottom = false,
                )
            }
        }
    }
}
