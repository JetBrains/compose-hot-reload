/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.devtools.sidecar

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import org.jetbrains.compose.devtools.Tag
import org.jetbrains.compose.devtools.tag
import org.jetbrains.compose.devtools.theme.DtColors
import org.jetbrains.compose.devtools.theme.DtPadding
import org.jetbrains.compose.devtools.theme.DtShapes
import org.jetbrains.compose.devtools.theme.dtVerticalPadding
import org.jetbrains.compose.devtools.widgets.DtCode
import org.jetbrains.compose.devtools.widgets.animatedReloadStatusBorder

@Composable
fun DtConsole(
    logs: List<String>,
    modifier: Modifier = Modifier,
) {
    val state = rememberLazyListState(0)

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            state.animateScrollToItem(logs.lastIndex)
        }
    }

    Box(
        modifier
            .tag(Tag.Console)
            .dtVerticalPadding()
            .animatedReloadStatusBorder()
            .clip(DtShapes.RoundedCornerShape)
            .background(DtColors.surfaceConsole)
            .fillMaxSize(),
    ) {
        SelectionContainer(modifier = Modifier.dtVerticalPadding()) {
            LazyColumn(
                state = state,
                contentPadding = PaddingValues(
                    horizontal = DtPadding.horizontal,
                    vertical = DtPadding.vertical
                ),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                items(logs) { log ->
                    DtCode(log)
                }
            }
        }
    }
}
