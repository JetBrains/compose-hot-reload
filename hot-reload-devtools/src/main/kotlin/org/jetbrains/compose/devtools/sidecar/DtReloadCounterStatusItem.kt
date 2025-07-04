/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.compose.devtools.sidecar

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import io.sellmair.evas.compose.composeValue
import org.jetbrains.compose.devtools.Tag
import org.jetbrains.compose.devtools.buildSystem
import org.jetbrains.compose.devtools.states.ReloadCountState
import org.jetbrains.compose.devtools.states.ReloadState
import org.jetbrains.compose.devtools.tag
import org.jetbrains.compose.devtools.theme.DtColors
import org.jetbrains.compose.devtools.theme.DtTextStyles
import org.jetbrains.compose.devtools.widgets.DtBuildSystemLogo
import org.jetbrains.compose.devtools.widgets.DtText

@Composable
fun DtExpandedReloadCounterStatusItem() {
    val state = ReloadCountState.composeValue()

    if (state.successfulReloads > 0) {
        DtSidecarStatusItem(
            symbol = {
                Icon(Icons.Filled.Refresh, "Reload", tint = DtColors.text)
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
fun DtMinimisedReloadCounterStatusItem() {
    val reloadState = ReloadState.composeValue()
    val countState = ReloadCountState.composeValue()

    val scale = when {
        countState.successfulReloads < 10 -> 1.0f
        else -> 0.9f
    }

    when (reloadState) {
        is ReloadState.Reloading -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.scale(scale).horizontalScroll(rememberScrollState())
            ) {
                CircularProgressIndicator(
                    strokeWidth = 4.dp, color = DtColors.statusColorOrange2,
                    modifier = Modifier.size(10.dp).padding(2.dp).tag(Tag.ReloadStatusSymbol)
                        .progressSemantics()
                )
                DtBuildSystemLogo(buildSystem, modifier = Modifier.size(20.dp).padding(2.dp))
            }
        }
        else -> {
            if (countState.successfulReloads < 1) return
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.scale(scale).horizontalScroll(rememberScrollState())
            ) {
                DtText(
                    text = "${countState.successfulReloads}",
                    modifier = Modifier.tag(Tag.ReloadCounterText),
                    style = DtTextStyles.smallSemiBold.copy(color = DtColors.text)
                )
            }
        }
    }
}
