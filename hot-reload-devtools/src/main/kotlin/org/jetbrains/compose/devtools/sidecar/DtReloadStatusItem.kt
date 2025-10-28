/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.compose.devtools.sidecar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.progressSemantics
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.sellmair.evas.compose.composeValue
import kotlinx.coroutines.delay
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.devtools.Tag
import org.jetbrains.compose.devtools.states.BuildSystemUIState
import org.jetbrains.compose.devtools.states.ReloadUIState
import org.jetbrains.compose.devtools.tag
import org.jetbrains.compose.devtools.theme.DtColors
import org.jetbrains.compose.devtools.theme.DtImages
import org.jetbrains.compose.devtools.theme.DtPadding
import org.jetbrains.compose.devtools.theme.DtTextStyles
import org.jetbrains.compose.devtools.widgets.DtBuildSystemLogo
import org.jetbrains.compose.devtools.widgets.DtImage
import org.jetbrains.compose.devtools.widgets.DtSmallText
import org.jetbrains.compose.devtools.widgets.DtText
import kotlin.time.Clock

@Composable
fun DtReloadStatusItem() {
    val reloadState = ReloadUIState.composeValue()

    when (reloadState) {
        is ReloadUIState.Reloading -> DtSidecarStatusItem(
            symbol = {
                CircularProgressIndicator(
                    strokeWidth = 2.dp, color = DtColors.statusColorOrange2,
                    modifier = Modifier.tag(Tag.ReloadStatusSymbol)
                        .progressSemantics()
                )
            },
            content = {
                val buildSystem = BuildSystemUIState.composeValue()?.buildSystem
                if (buildSystem != null) {
                    DtBuildSystemLogo(buildSystem, modifier = Modifier.padding(DtPadding.tiny))
                }
                DtText("Reloading...", Modifier.tag(Tag.ReloadStatusText))
            }
        )
        is ReloadUIState.Ok -> DtSidecarStatusItem(
            symbol = {
                DtImage(
                    DtImages.Image.GREEN_CHECKMARK_ICON,
                    contentDescription = "Success",
                    modifier = Modifier.tag(Tag.ReloadStatusSymbol)
                )
            },
            content = { ResultContent(reloadState) }
        )
        is ReloadUIState.Failed -> DtSidecarStatusItem(
            symbol = {
                DtImage(
                    DtImages.Image.ERROR_ICON,
                    contentDescription = "Error",
                    modifier = Modifier.tag(Tag.ReloadStatusSymbol)
                )
            },
            content = { ResultContent(reloadState) }
        )
    }
}

@Composable
private fun ResultContent(state: ReloadUIState) {
    var durationText by remember { mutableStateOf("") }

    LaunchedEffect(state) {
        while (true) {
            val duration = Clock.System.now() - state.time
            duration.toComponents { hours, minutes, seconds, _ ->
                durationText = when {
                    hours > 0 -> "$hours hours"
                    minutes > 0 -> "$minutes minutes"
                    else -> "$seconds seconds"
                }
            }
            delay(128)
        }
    }

    Row(
        verticalAlignment = CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DtPadding.mediumElementPadding)
    ) {
        StatusText(state)
        DtSmallText("$durationText ago")
    }
}

@Composable
private fun StatusText(state: ReloadUIState, modifier: Modifier = Modifier) {
    when (state) {
        is ReloadUIState.Failed -> FailedStatusText(state, modifier)
        is ReloadUIState.Ok -> SuccessStatusText(state, modifier)
        is ReloadUIState.Reloading -> Unit
    }
}

@Composable
private fun SuccessStatusText(state: ReloadUIState.Ok, modifier: Modifier = Modifier) {
    DtText(
        "Success: Last reload: ${state.formattedTime()}",
        modifier = modifier.tag(Tag.ReloadStatusText),
        maxLines = 1, overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun FailedStatusText(state: ReloadUIState.Failed, modifier: Modifier) {
    val controller = LocalDtNotificationsWindowController.current
    DtText(
        "Failed:${if (state.reason.isNotEmpty()) " ${state.reason}: " else ""} ${state.formattedTime()}",
        modifier = modifier.tag(Tag.ReloadStatusText)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(role = Role.Button) { controller.requestFocus() }
            .widthIn(max = 350.dp),
        maxLines = 1, overflow = TextOverflow.Ellipsis,
        style = DtTextStyles.code.copy(
            textDecoration = TextDecoration.Underline
        )
    )
}


private fun ReloadUIState.formattedTime(): String {
    val localTime = time.toLocalDateTime(TimeZone.currentSystemDefault()).time
    return localTime.format(LocalTime.Format {
        hour()
        char(':')
        minute()
        char(':')
        second()
    })
}
