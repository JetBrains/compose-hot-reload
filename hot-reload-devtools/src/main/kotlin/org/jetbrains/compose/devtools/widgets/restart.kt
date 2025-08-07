/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.devtools.widgets

import androidx.compose.runtime.Composable
import io.sellmair.evas.compose.EvasLaunching
import org.jetbrains.compose.devtools.send
import org.jetbrains.compose.reload.core.HotReloadEnvironment
import org.jetbrains.compose.reload.core.HotReloadProperty
import org.jetbrains.compose.reload.core.LaunchMode
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.info
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import kotlin.io.path.absolutePathString


private val logger = createLogger()

@Composable
internal fun restartAction(): () -> Unit = EvasLaunching {
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
}
