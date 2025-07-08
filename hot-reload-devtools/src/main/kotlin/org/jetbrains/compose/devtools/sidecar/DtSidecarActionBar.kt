/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.devtools.sidecar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.sellmair.evas.compose.EvasLaunching
import org.jetbrains.compose.devtools.Tag
import org.jetbrains.compose.devtools.send
import org.jetbrains.compose.devtools.sendAsync
import org.jetbrains.compose.devtools.theme.DtPadding
import org.jetbrains.compose.devtools.widgets.DtTextButton
import org.jetbrains.compose.reload.core.HotReloadEnvironment
import org.jetbrains.compose.reload.core.HotReloadProperty
import org.jetbrains.compose.reload.core.LaunchMode
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.info
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists


private val logger = createLogger()

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DtSidecarActionBar(modifier: Modifier = Modifier.Companion) {
    FlowRow(
        modifier = modifier.padding(vertical = DtPadding.medium),
        horizontalArrangement = Arrangement.spacedBy(DtPadding.medium),
        verticalArrangement = Arrangement.spacedBy(DtPadding.medium)
    ) {

        if (!HotReloadEnvironment.gradleBuildContinuous) {
            DtTextButton(
                text = "Reload",
                icon = Icons.Filled.Refresh,
                tag = Tag.ActionButton,
                onClick = {
                    OrchestrationMessage.RecompileRequest().sendAsync()
                }
            )
        }

        DtTextButton(
            text = "Retry Failed Compositions",
            icon = Icons.Filled.Refresh,
            tag = Tag.ActionButton,
            onClick = {
                OrchestrationMessage.RetryFailedCompositionRequest().sendAsync()
            }
        )

        if (
            (HotReloadEnvironment.argFile?.exists() == true &&
                HotReloadEnvironment.mainClass != null)
        ) {
            DtTextButton(
                text = "Restart",
                icon = Icons.Filled.Refresh,
                tag = Tag.ActionButton,
                onClick = EvasLaunching {
                logger.info("Restarting...")

                val processBuilder = ProcessBuilder(
                    System.getProperty("java.home") + "/bin/java",
                    "@" + (HotReloadEnvironment.argFile?.absolutePathString() ?: return@EvasLaunching),
                    "-D${HotReloadProperty.LaunchMode.key}=${LaunchMode.Detached.name}",
                    HotReloadEnvironment.mainClass ?: return@EvasLaunching,
                )

                HotReloadEnvironment.stdinFile?.let { file ->
                    processBuilder.redirectInput(file.toFile())
                }

                HotReloadEnvironment.stdoutFile?.let { file ->
                    processBuilder.redirectOutput(file.toFile())
                }

                HotReloadEnvironment.stderrFile?.let { file ->
                    processBuilder.redirectError(file.toFile())
                }

                logger.info("Restarting: ${processBuilder.command()}")
                processBuilder.start()

                logger.info("New process started; Exiting")
                OrchestrationMessage.ShutdownRequest("Requested by user through 'devtools'").send()
            })
        }


        DtTextButton(
            text = "Clean Composition",
            icon = Icons.Filled.Delete,
            tag = Tag.ActionButton,
            onClick = {
                OrchestrationMessage.CleanCompositionRequest().sendAsync()
            }
        )

        DtTextButton(
            text = "Exit",
            icon = Icons.Filled.Close,
            tag = Tag.ActionButton,
            onClick = {
                OrchestrationMessage.ShutdownRequest("Requested by user through 'devtools'").sendAsync()
            }
        )
    }
}
