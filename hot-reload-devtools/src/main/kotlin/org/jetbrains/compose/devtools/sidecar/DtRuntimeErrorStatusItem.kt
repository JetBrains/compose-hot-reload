/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.compose.devtools.sidecar

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.sellmair.evas.compose.composeValue
import org.jetbrains.compose.devtools.Tag
import org.jetbrains.compose.devtools.states.ErrorUIState
import org.jetbrains.compose.devtools.tag
import org.jetbrains.compose.devtools.theme.DtImages
import org.jetbrains.compose.devtools.theme.DtTextStyles
import org.jetbrains.compose.devtools.widgets.DtImage
import org.jetbrains.compose.devtools.widgets.DtText

@Composable
fun DtRuntimeErrorStatusItem() {
    val error = ErrorUIState.composeValue()
    val controller = LocalDtNotificationsWindowController.current

    error.errors.forEach { error ->
        DtSidecarStatusItem(
            symbol = {
                DtImage(
                    DtImages.Image.WARNING_ICON,
                    modifier = Modifier.tag(Tag.RuntimeErrorSymbol)
                )
            },
            content = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DtText("Runtime exception: ")

                    DtText(
                        "${error.value.title} (${error.value.message})",
                        modifier = Modifier.tag(Tag.RuntimeErrorText)
                            .pointerHoverIcon(PointerIcon.Hand)
                            .clickable(role = Role.Button) { controller.requestFocus() }
                            .widthIn(max = 250.dp),
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
