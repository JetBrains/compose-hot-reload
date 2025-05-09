/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.compose.devtools.sidecar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import io.sellmair.evas.compose.composeValue
import org.jetbrains.compose.devtools.Tag
import org.jetbrains.compose.devtools.states.ReloadCountState
import org.jetbrains.compose.devtools.tag
import org.jetbrains.compose.devtools.theme.DtTextStyles
import org.jetbrains.compose.devtools.widgets.DtText

@Composable
fun DtExpandedReloadCounterStatusItem() {
    val state = ReloadCountState.composeValue()

    if (state.successfulReloads > 0) {
        DtSidecarStatusItem(
            symbol = {
                Icon(Icons.Default.Refresh, "Reload")
            },
            content = {
                DtText(
                    "${state.successfulReloads} successful reloads",
                    modifier = Modifier.tag(Tag.ReloadCounterText)
                )
            }
        )
    }
}

@Composable
fun DtCollapsedReloadCounterStatusItem() {
    val state = ReloadCountState.composeValue()
    if (state.successfulReloads < 1) return

    val scale = when {
        state.successfulReloads < 10 -> 1.0f
        else -> 0.9f
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.scale(scale)
    ) {
        if (state.successfulReloads < 100) {
            Box(modifier = Modifier.size(12.dp)) {
                Icon(Icons.Default.Refresh, "Reload")
            }
        }
        DtText(
            text = "${state.successfulReloads}",
            modifier = Modifier.tag(Tag.ReloadCounterText),
            style = DtTextStyles.smallSemiBold
        )
    }
}
