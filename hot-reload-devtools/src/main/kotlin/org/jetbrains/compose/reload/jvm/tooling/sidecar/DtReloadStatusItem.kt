/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.compose.reload.jvm.tooling.sidecar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.progressSemantics
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import io.sellmair.evas.compose.composeValue
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.reload.jvm.tooling.Tag
import org.jetbrains.compose.reload.jvm.tooling.states.ReloadState
import org.jetbrains.compose.reload.jvm.tooling.tag
import org.jetbrains.compose.reload.jvm.tooling.theme.DtColors
import org.jetbrains.compose.reload.jvm.tooling.theme.DtPadding
import org.jetbrains.compose.reload.jvm.tooling.theme.DtTextStyles
import org.jetbrains.compose.reload.jvm.tooling.theme.dtHorizontalPadding
import org.jetbrains.compose.reload.jvm.tooling.theme.dtVerticalPadding
import org.jetbrains.compose.reload.jvm.tooling.widgets.DtCode
import org.jetbrains.compose.reload.jvm.tooling.widgets.DtHeader2
import org.jetbrains.compose.reload.jvm.tooling.widgets.DtSmallText
import org.jetbrains.compose.reload.jvm.tooling.widgets.DtText

@Composable
fun DtReloadStatusItem() {
    val reloadState = ReloadState.composeValue()

    when (reloadState) {
        is ReloadState.Reloading -> DtSidecarStatusItem(
            symbol = {
                CircularProgressIndicator(
                    strokeWidth = 2.dp, color = DtColors.statusColorOrange2,
                    modifier = Modifier.padding(4.dp).tag(Tag.ReloadStatusSymbol)
                        .progressSemantics()
                )
            },
            content = { DtText("Reloading...", Modifier.tag(Tag.ReloadStatusText)) }
        )
        is ReloadState.Ok -> DtSidecarStatusItem(
            symbol = { Icon(Icons.Default.Check, "Success", modifier = Modifier.tag(Tag.ReloadStatusSymbol)) },
            content = { ResultContent(reloadState) }
        )
        is ReloadState.Failed -> DtSidecarStatusItem(
            symbol = { Icon(Icons.Default.Close, "Error", modifier = Modifier.tag(Tag.ReloadStatusSymbol)) },
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

    Row(verticalAlignment = CenterVertically, horizontalArrangement = Arrangement.spacedBy(DtPadding.arrangement)) {
        StatusText(state, Modifier.weight(1f))
        DtSmallText("(${durationText} ago)")
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
            .clickable(role = Role.Button) { isDialogVisible = !isDialogVisible },
        maxLines = 1, overflow = TextOverflow.Ellipsis,
        style = DtTextStyles.code.copy(
            textDecoration = TextDecoration.Underline
        )
    )

    if (isDialogVisible) {
        ErrorDialogWindow(state, onCloseRequest = { isDialogVisible = false })
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

@Composable
private fun ErrorDialogWindow(
    state: ReloadState.Failed,
    onCloseRequest: () -> Unit
) {
    DialogWindow(
        onCloseRequest = onCloseRequest,
        state = rememberDialogState(size = DpSize(512.dp, 512.dp)),
        alwaysOnTop = true,
        title = state.reason
    ) {
        Column(Modifier.dtHorizontalPadding().dtVerticalPadding()) {
            DtHeader2("Reloading Code failed")
            DtCode(state.reason)
            DtConsole(
                logs = state.logs.map { it.message },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
