/*
 * Copyright 2024-2026 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.tests

import kotlinx.coroutines.channels.consume
import org.jetbrains.compose.devtools.api.WindowsState
import org.jetbrains.compose.reload.core.State
import org.jetbrains.compose.reload.core.asChannel
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.info
import org.jetbrains.compose.reload.core.launchTask
import org.jetbrains.compose.reload.core.withAsyncTrace
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import org.jetbrains.compose.reload.test.gradle.HotReloadTestFixture
import java.awt.GraphicsEnvironment

private val logger = createLogger()

/**
 * Returns `true` if the current environment supports interactive desktop operations.
 * Returns `false` in headless environments or under Windows Services.
 */
internal fun isInteractiveDesktopAvailable(): Boolean {
    if (GraphicsEnvironment.isHeadless()) return false
    if (System.getProperty("os.name")?.startsWith("Windows") == true) {
        val sessionName = System.getenv("SESSIONNAME")
        if (sessionName.isNullOrEmpty() || sessionName == "Services") return false
    }
    return true
}

/**
 * Launches a background task that sends ACK messages for all non-ACK orchestration messages.
 * Non-headless applications don't have a main loop that sends ACK messages,
 * so this is needed to prevent sync() from hanging.
 */
internal fun HotReloadTestFixture.launchAckSender() {
    launchTask {
        orchestration.messages.collect { message ->
            if (message !is OrchestrationMessage.Ack) {
                orchestration send OrchestrationMessage.Ack(message.messageId)
            }
        }
    }
}

/**
 * Suspends until the [windowsState] reports exactly one window.
 */
internal suspend fun awaitOneWindow(windowsState: State<WindowsState>) =
    withAsyncTrace("Await one window") {
        windowsState.asChannel().consume {
            while (true) {
                val state = receive()
                if (state.windows.size == 1) break
                else logger.info("Waiting for exactly one window: ${state.windows.size}")
            }
        }
    }
