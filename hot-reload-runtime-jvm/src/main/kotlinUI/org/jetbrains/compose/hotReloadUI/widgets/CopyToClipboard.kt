/*
 * Copyright 2024-2026 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.hotReloadUI.widgets

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.hot_reload.hot_reload_runtime_jvm.generated.resources.Res
import org.jetbrains.compose.hot_reload.hot_reload_runtime_jvm.generated.resources.copy
import org.jetbrains.compose.resources.painterResource
import java.awt.datatransfer.StringSelection

@Composable
internal fun CopyToClipboardButton(
    modifier: Modifier = Modifier,
    content: () -> String,
) {
    val copySvg = painterResource(Res.drawable.copy)
    val clipboard = LocalClipboard.current
    var copyAll by remember { mutableStateOf(false) }
    LaunchedEffect(copyAll) {
        if (copyAll) {
            clipboard.setClipEntry(ClipEntry(StringSelection(content())))
            copyAll = false
        }
    }

    Image(
        painter = copySvg,
        contentDescription = "Copy",
        colorFilter = ColorFilter.tint(Color.White),
        modifier = modifier.clickable(enabled = true, onClick = { copyAll = true }).size(15.dp),
    )
}
