/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.devtools.sidecar

import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.devtools.Tag
import org.jetbrains.compose.devtools.tag
import org.jetbrains.compose.devtools.theme.DtPadding
import org.jetbrains.compose.devtools.theme.DtShapes
import org.jetbrains.compose.devtools.widgets.DtCode
import org.jetbrains.compose.devtools.widgets.animatedReloadStatusBorder
import org.jetbrains.compose.devtools.widgets.dtBorder
import org.jetbrains.compose.devtools.widgets.dtScrollbarStyle

@Composable
fun DtConsole(
    logs: List<String>,
    modifier: Modifier = Modifier,
    scrollToBottom: Boolean = true,
    animateBorder: Boolean = true,
) {
    val verticalScrollState = rememberLazyListState()
    val horizontalScrollState = rememberScrollState()

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty() && scrollToBottom) {
            verticalScrollState.animateScrollToItem(logs.lastIndex)
        }
    }

    Box(
        modifier
            .tag(Tag.Console)
            .apply {
                if (animateBorder) animatedReloadStatusBorder(idleColor = Color.Gray, resetErrorState = true)
                else dtBorder()
            }
            .clip(DtShapes.RoundedCornerShape)
            .fillMaxSize(),
    ) {
        SelectionContainer {
            LazyColumn(
                state = verticalScrollState,
                contentPadding = PaddingValues(
                    horizontal = DtPadding.smallElementPadding,
                    vertical = DtPadding.smallElementPadding,
                ),
                modifier = Modifier.horizontalScroll(horizontalScrollState),
            ) {
                items(logs) { log ->
                    DtCode(log)
                }
            }
        }
        VerticalScrollbar(
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            adapter = rememberScrollbarAdapter(verticalScrollState),
            style = dtScrollbarStyle(),
        )
        HorizontalScrollbar(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            adapter = rememberScrollbarAdapter(horizontalScrollState),
            style = dtScrollbarStyle(),
        )
    }
}
