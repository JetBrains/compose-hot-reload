/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.devtools.sidecar

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import io.sellmair.evas.compose.composeValue
import kotlinx.coroutines.flow.collectLatest
import org.jetbrains.compose.devtools.Tag
import org.jetbrains.compose.devtools.states.NotificationsUIState
import org.jetbrains.compose.devtools.states.correspondingIcon
import org.jetbrains.compose.devtools.theme.DtColors
import org.jetbrains.compose.devtools.theme.DtPadding
import org.jetbrains.compose.devtools.theme.DtShapes
import org.jetbrains.compose.devtools.theme.DtSizes
import org.jetbrains.compose.devtools.theme.DtTitles
import org.jetbrains.compose.devtools.widgets.DtImage
import org.jetbrains.compose.devtools.widgets.DtNotificationCard
import org.jetbrains.compose.devtools.widgets.DtIconButton
import org.jetbrains.compose.devtools.widgets.DtWindowController
import org.jetbrains.compose.devtools.widgets.dtScrollbarStyle
import org.jetbrains.compose.devtools.widgets.rememberWindowController

@Composable
fun DtNotificationsButton() {
    val controller = LocalDtNotificationsWindowController.current
    val notifications = NotificationsUIState.composeValue()

    LaunchedEffect(notifications) {
        if (notifications.notifications.isEmpty()) {
            controller.close()
        }
    }

    if (notifications.notifications.isNotEmpty()) {
        DtIconButton(
            onClick = { controller.requestFocus() },
            tooltip = "Show notifications",
            tag = Tag.NotificationsButton,
        ) {
            DtImage(
                image = notifications.correspondingIcon,
                modifier = Modifier.size(DtSizes.logoSize).scale(0.75f),
            )
        }
    }

    if (controller.isOpen.value) {
        DtNotificationsWindow { controller.close() }
    }
}

@Composable
fun DtNotificationsWindow(onClose: () -> Unit) {
    val controller = LocalDtNotificationsWindowController.current

    Window(
        onCloseRequest = onClose,
        state = rememberWindowState(
            size = DtSizes.defaultWindowSize,
            position = WindowPosition.Aligned(Alignment.Center)
        ),
        title = "${DtTitles.COMPOSE_HOT_RELOAD} Notifications"
    ) {
        LaunchedEffect(Unit) {
            controller.focusRequests.collectLatest {
                if (controller.isOpen.value) {
                    window.toFront()
                    window.requestFocus()
                }
            }
        }

        DtNotificationsWindowContent()
    }
}

@Composable
internal fun DtNotificationsWindowContent() {
    val notifications = NotificationsUIState.composeValue()
    val verticalScrollState = rememberLazyListState()
    Box(
        Modifier
            .background(DtColors.applicationBackground)
            .clip(DtShapes.RoundedCornerShape)
            .padding(DtPadding.medium)
            .animateContentSize(alignment = Alignment.TopCenter),
    ) {
        SelectionContainer {
            LazyColumn(
                state = verticalScrollState,
                verticalArrangement = Arrangement.spacedBy(DtPadding.mediumElementPadding),
            ) {
                // newest on top
                items(notifications.notifications.asReversed()) { notification ->
                    key(notification) {
                        DtNotificationCard(notification)
                    }
                }
            }
        }
        VerticalScrollbar(
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            adapter = rememberScrollbarAdapter(verticalScrollState),
            style = dtScrollbarStyle(),
        )
    }
}

val LocalDtNotificationsWindowController = staticCompositionLocalOf<DtWindowController> {
    error("DtNotificationsWindowController not provided")
}

@Composable
fun ProvideNotificationsWindowController(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalDtNotificationsWindowController provides rememberWindowController()) {
        content()
    }
}