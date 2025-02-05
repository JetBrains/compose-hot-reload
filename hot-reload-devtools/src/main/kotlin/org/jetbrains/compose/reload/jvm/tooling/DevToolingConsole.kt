/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.jvm.tooling

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.sellmair.evas.compose.composeValue
import org.jetbrains.compose.reload.jvm.tooling.states.ConsoleLogState

@Composable
fun DevToolingConsole(tag: String, modifier: Modifier) {
    val listState = rememberLazyListState()
    var isExpanded by remember { mutableStateOf(true) }
    val logState = ConsoleLogState.Key(tag).composeValue()

    LaunchedEffect(logState) {
        if (logState.logs.isEmpty()) return@LaunchedEffect
        listState.scrollToItem(logState.logs.lastIndex)
    }

    Column(modifier = modifier) {
        ExpandButton(
            isExpanded = isExpanded,
            onClick = { expanded -> isExpanded = expanded },
        ) {
            Text(tag, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        AnimatedVisibility(isExpanded) {
            Card(Modifier.padding(8.dp).fillMaxWidth()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.padding(8.dp).wrapContentHeight()
                ) {
                    items(logState.logs) { text ->
                        Text(text, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun ExpandButton(
    isExpanded: Boolean,
    onClick: (expanded: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    children: @Composable RowScope.() -> Unit
) {
    val rotation by animateFloatAsState(if (isExpanded) 0f else -90f)

    Row(
        modifier = modifier
            .clickable(onClick = { onClick(!isExpanded) }),
        verticalAlignment = CenterVertically,
    ) {
        Icon(
            Icons.Default.ArrowDropDown, contentDescription = null,
            modifier = Modifier.rotate(rotation),
        )
        children()
    }
}
