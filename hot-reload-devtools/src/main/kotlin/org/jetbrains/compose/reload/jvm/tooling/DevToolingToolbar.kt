/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.jvm.tooling

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.sellmair.evas.compose.composeValue
import org.jetbrains.compose.reload.core.HotReloadEnvironment
import org.jetbrains.compose.reload.jvm.tooling.states.ReloadState
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DevToolingToolbar(modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier.padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {

        if (!HotReloadEnvironment.gradleBuildContinuous) {
            ActionButton("Reload", onClick = {
                OrchestrationMessage.RecompileRequest().send()
            })
        }

        ActionButton("Exit", onClick = {
            OrchestrationMessage.ShutdownRequest().send()
        })

        ActionButton("Retry Failed Compositions", onClick = {
            OrchestrationMessage.RetryFailedCompositionRequest().send()
        })


        ActionButton("Clean Composition", onClick = {
            OrchestrationMessage.CleanCompositionRequest().send()
        })

    }
}

@Composable
fun ActionButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val reloadState = ReloadState.composeValue()
    val reloadColor by animateReloadStateColor(reloadState)
    val reloadBrush = animateReloadingIndicatorBrush()
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = ButtonDefaults.outlinedShape,
        colors = ButtonDefaults.outlinedButtonColors(),
        border = ButtonDefaults.outlinedButtonBorder(true).run {
            if (reloadState is ReloadState.Reloading) copy(brush = reloadBrush)
            else copy(brush = SolidColor(reloadColor))
        },
        interactionSource = null,
    ) {
        Text(text, fontSize = 12.sp, modifier = Modifier.alignByBaseline())
    }
}
