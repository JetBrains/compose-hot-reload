/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.devtools.sidecar

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.sellmair.evas.compose.composeValue
import org.jetbrains.compose.devtools.states.ConsoleLogState
import org.jetbrains.compose.devtools.widgets.DtCopyToClipboardButton
import org.jetbrains.compose.devtools.widgets.DtHeader2

@Composable
fun DtMainConsole(
    modifier: Modifier = Modifier
) {
    val logState = ConsoleLogState.Key.composeValue()
    val scroll = rememberScrollState(0)

    LaunchedEffect(scroll.maxValue) {
        if (logState.logs.isEmpty()) return@LaunchedEffect
        scroll.scrollTo(scroll.maxValue)
    }

    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.Bottom,
        ) {
            DtHeader2("Console")
            Spacer(Modifier.weight(1f))
            DtCopyToClipboardButton("Copy all") { logState.logs.joinToString("\n") }
        }
        DtConsole(logs = logState.logs, modifier = Modifier.fillMaxSize())
    }
}
