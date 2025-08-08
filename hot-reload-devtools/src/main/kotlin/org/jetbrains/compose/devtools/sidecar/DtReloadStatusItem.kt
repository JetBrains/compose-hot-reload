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
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.devtools.Tag
import org.jetbrains.compose.devtools.states.BuildSystemState
import org.jetbrains.compose.devtools.states.ReloadState
import org.jetbrains.compose.devtools.tag
import org.jetbrains.compose.devtools.theme.DtColors
import org.jetbrains.compose.devtools.theme.DtImages
import org.jetbrains.compose.devtools.theme.DtPadding
import org.jetbrains.compose.devtools.theme.DtTextStyles
import org.jetbrains.compose.devtools.widgets.DtBuildSystemLogo
import org.jetbrains.compose.devtools.widgets.DtErrorDialogWindow
import org.jetbrains.compose.devtools.widgets.DtImage
import org.jetbrains.compose.devtools.widgets.DtSmallText
import org.jetbrains.compose.devtools.widgets.DtText

@Composable
fun DtReloadStatusItem() {
    val reloadState = ReloadState.composeValue()

    when (reloadState) {
        is ReloadState.Reloading -> DtSidecarStatusItem(
            symbol = {
                CircularProgressIndicator(
                    strokeWidth = 2.dp, color = DtColors.statusColorOrange2,
                    modifier = Modifier.tag(Tag.ReloadStatusSymbol)
                        .progressSemantics()
                )
            },
            content = {
                val buildSystem = BuildSystemState.composeValue()?.buildSystem
                if (buildSystem != null) {
                    DtBuildSystemLogo(buildSystem, modifier = Modifier.padding(2.dp))
                }
                DtText("Reloading...", Modifier.tag(Tag.ReloadStatusText))
            }
        )
        is ReloadState.Ok -> DtSidecarStatusItem(
            symbol = {
                DtImage(
                    DtImages.Image.GREEN_CHECKMARK_ICON,
                    contentDescription = "Success",
                    modifier = Modifier.tag(Tag.ReloadStatusSymbol)
                )
            },
            content = { ResultContent(reloadState) }
        )
        is ReloadState.Failed -> DtSidecarStatusItem(
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
private fun ResultContent(state: ReloadState) {
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
private fun StatusText(state: ReloadState, modifier: Modifier = Modifier) {
    when (state) {
        is ReloadState.Failed -> FailedStatusText(state, modifier)
        is ReloadState.Ok -> SuccessStatusText(state, modifier)
        is ReloadState.Reloading -> Unit
    }
}

@Composable
private fun SuccessStatusText(state: ReloadState.Ok, modifier: Modifier = Modifier) {
    DtText(
        "Success: Last reload: ${state.formattedTime()}",
        modifier = modifier.tag(Tag.ReloadStatusText),
        maxLines = 1, overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun FailedStatusText(state: ReloadState.Failed, modifier: Modifier) {
    var isDialogVisible by remember { mutableStateOf(false) }

    DtText(
        "Failed:${if (state.reason.isNotEmpty()) " ${state.reason}: " else ""} ${state.formattedTime()}",
        modifier = modifier.tag(Tag.ReloadStatusText)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(role = Role.Button) { isDialogVisible = !isDialogVisible }
            .widthIn(max = 350.dp),
        maxLines = 1, overflow = TextOverflow.Ellipsis,
        style = DtTextStyles.code.copy(
            textDecoration = TextDecoration.Underline
        )
    )

    if (isDialogVisible) {
        DtErrorDialogWindow(
            title = "Reloading Code failed",
            message = state.reason,
            logs = state.logs.map { it.message },
            onCloseRequest = { isDialogVisible = false }
        )
    }
}


private fun ReloadState.formattedTime(): String {
    val localTime = time.toLocalDateTime(TimeZone.currentSystemDefault()).time
    return localTime.format(LocalTime.Format {
        hour()
        char(':')
        minute()
        char(':')
        second()
    })
}
