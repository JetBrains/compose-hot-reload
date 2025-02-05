/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.jvm.tooling

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.LogMessage

@Composable
internal fun DevToolingWidget(modifier: Modifier = Modifier) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.fillMaxSize()
    ) {
        DevToolingToolbar()

        val consoleWeight = Modifier.weight(1f, fill = false)
        DevToolingConsole(LogMessage.TAG_COMPILER, consoleWeight)
        DevToolingConsole(LogMessage.TAG_AGENT, consoleWeight)
        DevToolingConsole(LogMessage.TAG_RUNTIME, consoleWeight)
    }
}
