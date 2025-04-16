/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.jvm.tooling.sidecar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.sellmair.evas.compose.EvasLaunching
import org.jetbrains.compose.reload.core.HotReloadEnvironment
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.jvm.tooling.send
import org.jetbrains.compose.reload.jvm.tooling.widgets.DtTextButton
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists


private val logger = createLogger()

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DtSidecarActionBar(modifier: Modifier = Modifier.Companion) {
    FlowRow(
        modifier = modifier.padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {

        if (!HotReloadEnvironment.gradleBuildContinuous) {
            DtTextButton("Reload", onClick = {
                OrchestrationMessage.RecompileRequest().send()
            })
        }

        DtTextButton("Exit", onClick = {
            OrchestrationMessage.ShutdownRequest("Requested by user through 'devtools'").send()
        })

        if (
            (HotReloadEnvironment.argFile?.exists() == true &&
                HotReloadEnvironment.mainClass != null)
        ) {
            DtTextButton("Restart", onClick = EvasLaunching {
                logger.info("Restarting...")

                ProcessBuilder(
                    System.getProperty("java.home") + "/bin/java",
                    "@" + (HotReloadEnvironment.argFile?.absolutePathString() ?: return@EvasLaunching),
                    HotReloadEnvironment.mainClass ?: return@EvasLaunching,
                ).apply {
                    logger.info("Restarting: ${this.command()}")
                }.redirectErrorStream(true).start()

                logger.info("New process started; Exiting")
                OrchestrationMessage.ShutdownRequest("Requested by user through 'devtools'").send()
            })
        }

        DtTextButton("Retry Failed Compositions", onClick = {
            OrchestrationMessage.RetryFailedCompositionRequest().send()
        })


        DtTextButton("Clean Composition", onClick = {
            OrchestrationMessage.CleanCompositionRequest().send()
        })
    }
}
