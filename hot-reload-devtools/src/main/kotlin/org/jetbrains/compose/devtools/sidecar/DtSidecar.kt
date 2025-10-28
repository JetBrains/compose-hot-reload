/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.devtools.sidecar

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.WindowState
import org.jetbrains.compose.devtools.Tag
import org.jetbrains.compose.devtools.sendAsync
import org.jetbrains.compose.devtools.theme.DtImages
import org.jetbrains.compose.devtools.theme.DtPadding
import org.jetbrains.compose.devtools.theme.DtShapes
import org.jetbrains.compose.devtools.theme.DtSizes
import org.jetbrains.compose.devtools.theme.DtTitles.COMPOSE_HOT_RELOAD_TITLE
import org.jetbrains.compose.devtools.widgets.DtComposeLogo
import org.jetbrains.compose.devtools.widgets.DtImage
import org.jetbrains.compose.devtools.widgets.DtIconButton
import org.jetbrains.compose.devtools.widgets.animateReloadStatusColor
import org.jetbrains.compose.devtools.widgets.applyIf
import org.jetbrains.compose.devtools.widgets.dtBackground
import org.jetbrains.compose.reload.core.HotReloadEnvironment
import org.jetbrains.compose.reload.core.WindowId
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage

private val cornerShape = if (devToolsUseTransparency) DtShapes.RoundedCornerShape else DtShapes.SquareCornerShape

@Composable
fun DtSidecarWindow(
    windowId: WindowId,
    windowState: WindowState,
    isAlwaysOnTop: Boolean,
) {
    DtAnimatedWindow(
        windowId, windowState,
        title = COMPOSE_HOT_RELOAD_TITLE,
        alwaysOnTop = isAlwaysOnTop,
        visible = true,
        size = sidecarWindowSize,
    ) {
        DtSidecarWindowContent()
    }
}

@Composable
internal fun DtSidecarWindowContent(
    modifier: Modifier = Modifier,
) {
    DtCompositionLocalProvider {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(DtPadding.tinyElementPadding),
            modifier = modifier
                .dtBackground(cornerShape)
                .padding(DtPadding.small)
                .animateContentSize(alignment = Alignment.TopCenter)
                .applyIf(!devToolsUseTransparency) { fillMaxSize() }
        ) {
            DtComposeLogo(
                tooltip = "Compose Hot Reload Dev Tools",
                modifier = Modifier.size(DtSizes.largeLogoSize).padding(DtPadding.small),
                tint = animateReloadStatusColor(Color.White).value
            )

            DtSidecarReloadButton()
            DtSidecarResetButton()
            DtShowLogsButton()
            DtSidecarRestartButton()
            DtSidecarCloseButton()

            DtNotificationsButton()

            DtReloadCounterStatusItem(
                modifier = Modifier.size(DtSizes.reloadCounterSize),
                showDefaultValue = !devToolsUseTransparency,
            )
        }
    }
}

@Composable
private fun DtSidecarReloadButton() {
    if (!HotReloadEnvironment.gradleBuildContinuous) {
        DtIconButton(
            onClick = { OrchestrationMessage.RecompileRequest().sendAsync() },
            tooltip = "Reload UI",
            tag = Tag.ActionButton,
        ) {
            DtImage(
                image = DtImages.Image.SYNC_ICON,
                modifier = Modifier.size(DtSizes.iconSize),
                tint = Color.White,
            )
        }
    }
}

@Composable
private fun DtSidecarResetButton() {
    DtIconButton(
        onClick = { OrchestrationMessage.CleanCompositionRequest().sendAsync() },
        tooltip = "Reset UI",
        tag = Tag.ActionButton,
    ) {
        DtImage(
            image = DtImages.Image.DELETE_ICON,
            modifier = Modifier.size(DtSizes.iconSize),
            tint = Color.White,
        )
    }
}

@Composable
private fun DtSidecarRestartButton() {
    DtIconButton(
        onClick = { OrchestrationMessage.RestartRequest().sendAsync() },
        tooltip = "Restart the application",
        tag = Tag.ActionButton,
    ) {
        DtImage(
            image = DtImages.Image.RESTART_ICON,
            modifier = Modifier.size(DtSizes.iconSize),
            tint = Color.White,
        )
    }
}

@Composable
private fun DtSidecarCloseButton() {
    DtIconButton(
        onClick = { OrchestrationMessage.ShutdownRequest("Requested by user through 'devtools'").sendAsync() },
        tooltip = "Close the application",
        tag = Tag.ActionButton,
    ) {
        DtImage(
            image = DtImages.Image.CLOSE_ICON,
            modifier = Modifier.size(DtSizes.iconSize),
            tint = Color.White,
        )
    }
}