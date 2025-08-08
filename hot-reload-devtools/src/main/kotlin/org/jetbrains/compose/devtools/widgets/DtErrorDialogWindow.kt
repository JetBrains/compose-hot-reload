/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.devtools.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import org.jetbrains.compose.devtools.sidecar.DtConsole
import org.jetbrains.compose.devtools.theme.DtColors
import org.jetbrains.compose.devtools.theme.DtPadding
import org.jetbrains.compose.devtools.theme.DtSizes


@Composable
internal fun DtErrorDialogWindow(
    title: String,
    message: String,
    logs: List<String>,
    onCloseRequest: () -> Unit
) {
    DialogWindow(
        onCloseRequest = onCloseRequest,
        state = rememberDialogState(size = DtSizes.defaultWindowSize),
        alwaysOnTop = true,
        title = title,
    ) {
        Column(
            modifier = Modifier.background(DtColors.applicationBackground).padding(DtPadding.borderPadding),
            verticalArrangement = Arrangement.spacedBy(DtPadding.smallElementPadding),
        ) {
            Row {
                DtHeader2(title)
                Spacer(Modifier.weight(1f))
                DtCopyToClipboardButton {
                    buildString {
                        appendLine(message)
                        append(logs.joinToString("\n"))
                    }
                }
            }
            DtCode(message)
            DtConsole(
                logs = logs,
                modifier = Modifier.fillMaxSize(),
                scrollToBottom = false,
            )
        }
    }
}
