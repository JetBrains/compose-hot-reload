/*
 * Copyright 2024-2026 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.hotReloadUI.widgets

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.hot_reload.hot_reload_runtime_jvm.generated.resources.Res
import org.jetbrains.compose.hot_reload.hot_reload_runtime_jvm.generated.resources.copy
import org.jetbrains.compose.resources.painterResource
import java.awt.datatransfer.StringSelection

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CopyToClipboardButton(
    icon: Painter = painterResource(Res.drawable.copy),
    tooltip: String = "Copy to clipboard",
    modifier: Modifier = Modifier,
    content: () -> String,
) {
    val clipboard = LocalClipboard.current
    var copyAll by remember { mutableStateOf(false) }
    LaunchedEffect(copyAll) {
        if (copyAll) {
            clipboard.setClipEntry(ClipEntry(StringSelection(content())))
            copyAll = false
        }
    }

    val shape = RoundedCornerShape(4.dp)
    TooltipArea(
        tooltip = {
            Box(
                modifier = Modifier
                    .clip(shape)
                    .border(1.dp, border, shape)
            ) {
                Box(modifier = Modifier.background(surface).padding(4.dp)) {
                    BasicText(
                        tooltip,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle.Default.copy(fontSize = 12.sp),
                        color = ColorProducer { Color.White }
                    )
                }
            }
        }
    ) {
        Image(
            painter = icon,
            contentDescription = "Copy",
            colorFilter = ColorFilter.tint(Color.White),
            modifier = modifier.clickable(enabled = true, onClick = { copyAll = true }).size(15.dp),
        )
    }
}

private val surface = Color(0xFF2B2D30)
private val border = Color(0xFF5E6060)
