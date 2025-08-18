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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.sellmair.evas.compose.composeValue
import org.jetbrains.compose.devtools.Tag
import org.jetbrains.compose.devtools.states.BuildSystemUIState
import org.jetbrains.compose.devtools.states.ReloadCountUIState
import org.jetbrains.compose.devtools.states.ReloadUIState
import org.jetbrains.compose.devtools.tag
import org.jetbrains.compose.devtools.theme.DtColors
import org.jetbrains.compose.devtools.theme.DtImages
import org.jetbrains.compose.devtools.theme.DtTextStyles
import org.jetbrains.compose.devtools.widgets.DtBuildSystemLogo
import org.jetbrains.compose.devtools.widgets.DtImage
import org.jetbrains.compose.devtools.widgets.DtText
import org.jetbrains.compose.devtools.widgets.bouncing
import org.jetbrains.compose.devtools.widgets.shaking

@Composable
fun DtExpandedReloadCounterStatusItem() {
    val state = ReloadCountUIState.composeValue()

    if (state.successfulReloads > 0) {
        DtSidecarStatusItem(
            symbol = {
                DtImage(DtImages.Image.RESTART_ICON, tint = DtColors.text)
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
fun DtMinimisedReloadCounterStatusItem(showDefaultValue: Boolean = false) {
    val reloadState = ReloadUIState.composeValue()
    val countState = ReloadCountUIState.composeValue()

    val scale = when {
        countState.successfulReloads < 10 -> 1.0f
        else -> 0.9f
    }

    when (reloadState) {
        is ReloadUIState.Reloading -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.scale(scale).horizontalScroll(rememberScrollState())
            ) {
                val buildSystem = BuildSystemUIState.composeValue()?.buildSystem

                if (buildSystem == null)
                    CircularProgressIndicator(
                        strokeWidth = 4.dp, color = DtColors.statusColorOrange2,
                        modifier = Modifier.size(10.dp).padding(2.dp).tag(Tag.ReloadStatusSymbol)
                            .progressSemantics()
                    )

                if (buildSystem != null) {
                    DtBuildSystemLogo(
                        buildSystem,
                        modifier = Modifier.size(20.dp).padding(2.dp)
                            .bouncing(min = 0.9f, max = 1.10f, 250)
                            .shaking(min = -5f, max = 5f, 128)
                    )
                }
            }
        }
        else -> {
            if (countState.successfulReloads < 1 && !showDefaultValue) return
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.scale(scale).horizontalScroll(rememberScrollState())
            ) {
                DtText(
                    text = "${countState.successfulReloads}",
                    modifier = Modifier.tag(Tag.ReloadCounterText),
                    style = DtTextStyles.smallSemiBold.copy(color = DtColors.text).copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}
