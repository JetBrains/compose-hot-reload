/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.jvm.tooling

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Card
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.sellmair.evas.compose.composeValue
import org.jetbrains.compose.reload.jvm.tooling.states.ConsoleLogState

@Composable
fun DevToolingConsole(tag: String, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    val logState = ConsoleLogState.Key(tag).composeValue()
    val scroll = rememberScrollState(0)


    LaunchedEffect(logState) {
        if (logState.logs.isEmpty()) return@LaunchedEffect
        listState.scrollToItem(logState.logs.lastIndex)
    }

    Column(modifier = modifier) {
        Text(tag, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Card(Modifier.padding(vertical = 8.dp).fillMaxSize()) {
            SelectionContainer {
                Text(
                    logState.logs.joinToString("\n"),
                    modifier = Modifier.verticalScroll(scroll).padding(8.dp)
                )
            }
        }
    }
}
