/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.devtools

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.reload.core.HotReloadEnvironment
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.info
import org.jetbrains.compose.reload.core.withType
import org.jetbrains.compose.reload.orchestration.OrchestrationHandle
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import kotlin.io.path.absolutePathString

private val logger = createLogger()

internal fun CoroutineScope.launchRestartActor(
    orchestration: OrchestrationHandle = org.jetbrains.compose.devtools.orchestration
) = launch {
    orchestration.messages.withType<OrchestrationMessage.RestartRequest>().collect {
        logger.info("Restarting...")

        val processBuilder = ProcessBuilder(
            System.getProperty("java.home") + "/bin/java",
            "@" + (HotReloadEnvironment.argFile?.absolutePathString() ?: return@collect),
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
}
