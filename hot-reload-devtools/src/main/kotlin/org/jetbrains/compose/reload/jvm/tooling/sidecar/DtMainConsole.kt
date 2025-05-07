/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.jvm.tooling.sidecar

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import io.sellmair.evas.compose.composeValue
import org.jetbrains.compose.reload.jvm.tooling.states.ConsoleLogState
import org.jetbrains.compose.reload.jvm.tooling.theme.dtHorizontalPadding
import org.jetbrains.compose.reload.jvm.tooling.widgets.DtHeader2

@Composable
fun DtMainConsole(
    modifier: Modifier = Modifier
) {
    val logState = ConsoleLogState.composeValue()
    val scroll = rememberScrollState(0)

    LaunchedEffect(scroll.maxValue) {
        if (logState.logs.isEmpty()) return@LaunchedEffect
        scroll.scrollTo(scroll.maxValue)
    }

    Column(modifier = modifier) {
        DtHeader2("Console", modifier = Modifier.dtHorizontalPadding())
        DtConsole(logs = logState.logs, modifier = Modifier.fillMaxSize())
    }
}
