/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.compose.reload.jvm.tooling.sidecar

import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import io.sellmair.evas.compose.composeValue
import org.jetbrains.compose.reload.jvm.tooling.Tag.ReloadCounterText
import org.jetbrains.compose.reload.jvm.tooling.states.ReloadCountState
import org.jetbrains.compose.reload.jvm.tooling.tag
import org.jetbrains.compose.reload.jvm.tooling.widgets.DtText

@Composable
fun DtExpandedReloadCounterStatusItem() {
    val state = ReloadCountState.composeValue()

    if (state.successfulReloads > 0) {
        DtSidecarStatusItem(
            symbol = {
                Icon(Icons.Default.Refresh, "Reload")
            },
            content = {
                DtText("${state.successfulReloads} successful reloads", modifier = Modifier.tag(ReloadCounterText))
            }
        )
    }
}

@Composable
fun DtCollapsedReloadCounterStatusItem() {
    val state = ReloadCountState.composeValue()
    if (state.successfulReloads < 1) return

    val defaultText = "⟳${state.successfulReloads}"
    val defaultModifier = Modifier.tag(ReloadCounterText)
    val (text, modifier) = when {
        state.successfulReloads < 10 -> defaultText to defaultModifier
        state.successfulReloads < 100 -> defaultText to defaultModifier.scale(0.9f)
        else -> defaultText.drop(1) to defaultModifier.scale(0.7f)
    }
    DtText(text = text, modifier = modifier)
}
