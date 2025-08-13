/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.compose.devtools.sidecar

import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import org.jetbrains.compose.devtools.theme.DtImages
import org.jetbrains.compose.devtools.theme.DtTextStyles
import org.jetbrains.compose.devtools.widgets.DtErrorDialogWindow
import org.jetbrains.compose.devtools.widgets.DtImage
import org.jetbrains.compose.devtools.widgets.DtText

@Composable
fun DtMissingJbrStatusItem() {
    val vendor = System.getProperty("java.vendor")
    if (vendor.contains("JetBrains", ignoreCase = true)) return

    var isDialogVisible by remember { mutableStateOf(false) }

    DtSidecarStatusItem(
        symbol = { DtImage(DtImages.Image.WARNING_ICON, "Missing JBR") },
        content = {
            DtText(
                "Not running on 'JetBrains Runtime' (Current jvm: $vendor)",
                modifier = Modifier
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable(role = Role.Button) { isDialogVisible = !isDialogVisible },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = DtTextStyles.code.copy(
                    textDecoration = TextDecoration.Underline,
                )
            )
        }
    )

    if (isDialogVisible) {
        DtErrorDialogWindow(
            title = "Not running on 'JetBrains Runtime'",
            message = "Current JVM info",
            logs = buildList {
                add("JVM name: ${System.getProperty("java.vm.name") ?: "N/A"}")
                add("JVM vendor: ${System.getProperty("java.vm.vendor") ?: "N/A"}")
                add("JVM version: ${System.getProperty("java.vm.version") ?: "N/A"}")
                add("JVM home: ${System.getProperty("java.home") ?: "N/A"}")
            },
            onCloseRequest = { isDialogVisible = false }
        )
    }
}
