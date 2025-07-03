/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.compose.devtools.sidecar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.DialogWindow
import io.sellmair.evas.compose.composeValue
import org.jetbrains.compose.devtools.Tag
import org.jetbrains.compose.devtools.states.UIErrorDescription
import org.jetbrains.compose.devtools.states.UIErrorState
import org.jetbrains.compose.devtools.tag
import org.jetbrains.compose.devtools.theme.DtColors
import org.jetbrains.compose.devtools.theme.DtPadding
import org.jetbrains.compose.devtools.theme.DtTextStyles
import org.jetbrains.compose.devtools.theme.dtHorizontalPadding
import org.jetbrains.compose.devtools.theme.dtVerticalPadding
import org.jetbrains.compose.devtools.widgets.DtCode
import org.jetbrains.compose.devtools.widgets.DtCopyToClipboardButton
import org.jetbrains.compose.devtools.widgets.DtHeader2
import org.jetbrains.compose.devtools.widgets.DtText
import org.jetbrains.compose.devtools.widgets.DtTextButton

@Composable
fun DtRuntimeErrorStatusItem() {
    val error = UIErrorState.composeValue()

    error.errors.forEach { error ->
        var isDialogVisible by remember { mutableStateOf(false) }

        ErrorDialogWindow(
            error.value, isDialogVisible, { isDialogVisible = false }
        )

        DtSidecarStatusItem(
            symbol = {
                Icon(
                    Icons.Filled.Warning, "Error", tint = DtColors.statusColorError,
                    modifier = Modifier.tag(Tag.RuntimeErrorSymbol)
                )
            },
            content = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DtText("Exception: ")

                    DtText(
                        "${error.value.title} (${error.value.message})",
                        modifier = Modifier.tag(Tag.RuntimeErrorText)
                            .pointerHoverIcon(PointerIcon.Hand)
                            .clickable(role = Role.Button) { isDialogVisible = !isDialogVisible },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = DtTextStyles.code.copy(
                            textDecoration = TextDecoration.Underline,
                        )
                    )
                }
            }
        )
    }
}

@Composable
private fun ErrorDialogWindow(
    error: UIErrorDescription,
    visible: Boolean,
    onCloseRequest: () -> Unit
) {
    if (!visible) return
    DialogWindow(
        onCloseRequest = onCloseRequest,
        title = error.title,
    ) {
        Column(
            modifier =
                Modifier.background(DtColors.applicationBackground)
                    .fillMaxSize()
                    .dtVerticalPadding()
                    .dtHorizontalPadding(),
            verticalArrangement = Arrangement.spacedBy(DtPadding.vertical)
        ) {
            DtHeader2("${error.title}: ${error.message}".trim())

            Row(horizontalArrangement = Arrangement.spacedBy(DtPadding.horizontal)) {
                if (error.recovery != null) {
                    DtTextButton("Retry", onClick = error.recovery)
                }

                DtCopyToClipboardButton("Copy Stacktrace") { error.stacktrace.joinToString("\n") }
            }

            SelectionContainer(modifier = Modifier.weight(1f)) {
                DtCode(
                    error.stacktrace.joinToString("\n"),
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                )
            }
        }
    }
}
