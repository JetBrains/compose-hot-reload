/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.devtools.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import io.sellmair.evas.compose.EvasLaunching
import io.sellmair.evas.compose.LocalEvents
import io.sellmair.evas.compose.rememberEvasCoroutineScope
import io.sellmair.evas.emit
import kotlinx.coroutines.launch
import org.jetbrains.compose.devtools.Tag
import org.jetbrains.compose.devtools.sidecar.DtConsole
import org.jetbrains.compose.devtools.states.UINotification
import org.jetbrains.compose.devtools.states.UINotificationDisposeEvent
import org.jetbrains.compose.devtools.states.correspondingIcon
import org.jetbrains.compose.devtools.tag
import org.jetbrains.compose.devtools.theme.DtColors
import org.jetbrains.compose.devtools.theme.DtImages
import org.jetbrains.compose.devtools.theme.DtPadding
import org.jetbrains.compose.devtools.theme.DtShapes
import org.jetbrains.compose.devtools.theme.DtSizes


@Composable
fun DtNotificationCard(notification: UINotification) {
    var showDetails by remember { mutableStateOf(false) }
    val scope = rememberEvasCoroutineScope()

    Column(
        modifier = Modifier
            .background(DtColors.applicationBackground)
            .dtBorder()
            .clip(DtShapes.RoundedCornerShape)
            .padding(DtPadding.smallElementPadding)
            .heightIn(max = DtSizes.maxNotificationCardHeight)
            .tag(Tag.NotificationCard),
        verticalArrangement = Arrangement.spacedBy(DtPadding.smallElementPadding),
    ) {
        Row {
            Row(horizontalArrangement = Arrangement.spacedBy(DtPadding.tinyElementPadding)) {
                DtNotificationIcon(notification)
                DtHeader2(notification.title, modifier = Modifier.tag(Tag.NotificationTitle))
            }
            Spacer(Modifier.weight(1f))

            Row(horizontalArrangement = Arrangement.spacedBy(DtPadding.tinyElementPadding)) {
                DtCopyToClipboardButton(
                    modifier = Modifier.size(DtSizes.iconSize),
                ) {
                    buildString {
                        appendLine(notification.message)
                        append(notification.details.joinToString("\n"))
                    }
                }

                if (notification.details.isNotEmpty()) {
                    DtIconButton(
                        onClick = { showDetails = !showDetails },
                        tooltip = "Show details",
                        modifier = Modifier.size(DtSizes.iconSize),
                        tag = Tag.NotificationExpandButton,
                    ) {
                        DtImage(
                            image = DtImages.Image.EXPAND_ICON,
                            tint = Color.White,
                        )
                    }
                }

                if (notification.isDisposableFromUI) {
                    DtIconButton(
                        onClick = { scope.launch { UINotificationDisposeEvent(notification.id).emit() } },
                        tooltip = "Clean the warning",
                        modifier = Modifier.size(DtSizes.iconSize),
                        tag = Tag.NotificationCleanButton,
                    ) {
                        DtImage(
                            image = DtImages.Image.CLOSE_ICON,
                            tint = Color.White,
                        )
                    }
                }
            }
        }

        DtCode(notification.message, modifier = Modifier.tag(Tag.NotificationMessage))

        if (showDetails) {
            DtConsole(
                logs = notification.details,
                scrollToBottom = false,
                animateBorder = false,
            )
        }
    }
}

@Composable
private fun DtNotificationIcon(notification: UINotification) = DtImage(
    image = notification.correspondingIcon,
    modifier = Modifier.tag(Tag.NotificationIcon),
)
