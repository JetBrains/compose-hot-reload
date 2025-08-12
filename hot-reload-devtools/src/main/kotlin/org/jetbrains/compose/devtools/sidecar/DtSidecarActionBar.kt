/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.devtools.sidecar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.devtools.Tag
import org.jetbrains.compose.devtools.sendAsync
import org.jetbrains.compose.devtools.theme.DtImages
import org.jetbrains.compose.devtools.theme.DtPadding
import org.jetbrains.compose.devtools.widgets.DtTextButton
import org.jetbrains.compose.devtools.widgets.restartAction
import org.jetbrains.compose.reload.core.HotReloadEnvironment
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import kotlin.io.path.exists


@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DtSidecarActionBar(modifier: Modifier = Modifier.Companion) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(DtPadding.mediumElementPadding),
        verticalArrangement = Arrangement.spacedBy(DtPadding.mediumElementPadding)
    ) {

        if (!HotReloadEnvironment.gradleBuildContinuous) {
            DtTextButton(
                text = "Reload",
                icon = DtImages.Image.RESTART_ICON,
                tag = Tag.ActionButton,
                onClick = {
                    OrchestrationMessage.RecompileRequest().sendAsync()
                }
            )
        }

        DtTextButton(
            text = "Reset UI",
            icon = DtImages.Image.DELETE_ICON,
            tag = Tag.ActionButton,
            onClick = {
                OrchestrationMessage.CleanCompositionRequest().sendAsync()
            }
        )

        if (
            (HotReloadEnvironment.argFile?.exists() == true &&
                HotReloadEnvironment.mainClass != null)
        ) {
            DtTextButton(
                text = "Restart",
                icon = DtImages.Image.RESTART_ICON,
                tag = Tag.ActionButton,
                onClick = restartAction()
            )
        }

        DtTextButton(
            text = "Exit",
            icon = DtImages.Image.CLOSE_ICON,
            tag = Tag.ActionButton,
            onClick = {
                OrchestrationMessage.ShutdownRequest("Requested by user through 'devtools'").sendAsync()
            }
        )
    }
}
