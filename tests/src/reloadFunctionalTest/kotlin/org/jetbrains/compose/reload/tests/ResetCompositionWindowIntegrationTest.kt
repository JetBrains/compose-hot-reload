/*
 * Copyright 2024-2026 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.tests

import kotlinx.coroutines.channels.consume
import org.jetbrains.compose.devtools.api.WindowsState
import org.jetbrains.compose.reload.core.asChannel
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.CleanCompositionRequest
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.WindowResizeRequest
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.WindowResizeResult
import org.jetbrains.compose.reload.test.gradle.Headless
import org.jetbrains.compose.reload.test.gradle.HotReloadTest
import org.jetbrains.compose.reload.test.gradle.HotReloadTestFixture
import org.jetbrains.compose.reload.test.gradle.initialSourceCode
import org.jetbrains.compose.reload.utils.HostIntegrationTest
import org.jetbrains.compose.reload.utils.QuickTest
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.assertTrue

class ResetCompositionWindowIntegrationTest {

    /**
     * CMP-10603: The 'Reset UI' action ('CleanCompositionRequest') must not reset the window
     * position and size. A hard reset tears the composition down and recomposes it, recreating a
     * 'WindowState' that lives inside the composition (here: 'rememberWindowState') with its default
     * geometry. The runtime is expected to restore the previous window bounds onto the recreated
     * window.
     */
    @Headless(false)
    @HostIntegrationTest
    @HotReloadTest
    @QuickTest
    fun `test - reset UI preserves window position and size`(fixture: HotReloadTestFixture) = fixture.runTest {
        assumeTrue(isInteractiveDesktopAvailable(), "Test requires an interactive desktop")
        fixture.launchAckSender()

        val windowsState = fixture.orchestration.states.get(WindowsState)

        fixture initialSourceCode """
            import androidx.compose.foundation.layout.Box
            import androidx.compose.ui.unit.dp
            import androidx.compose.ui.window.*
            import org.jetbrains.compose.reload.test.TestText

            fun main() {
                application {
                    Window(
                        onCloseRequest = ::exitApplication,
                        state = rememberWindowState(width = 400.dp, height = 300.dp),
                        undecorated = true,
                    ) {
                        Box { TestText("Reset me") }
                    }
                }
            }
            """.trimIndent()

        awaitOneWindow(windowsState)
        val (windowId, before) = windowsState.value.windows.entries.first()

        val targetWidth = before.width + 123
        val targetHeight = before.height + 77
        val request = WindowResizeRequest(width = targetWidth, height = targetHeight, windowId = windowId)
        val result = fixture.sendMessage(request) {
            skipToMessage<WindowResizeResult> { it.windowResizeRequestId == request.messageId }
        }
        assertTrue(result.isSuccess, "Resize failed: ${result.errorMessage}")
        awaitWindowSize(windowsState, width = targetWidth, height = targetHeight)

        val resized = windowsState.value.windows.getValue(windowId)

        fixture.orchestration send CleanCompositionRequest()

        windowsState.asChannel().consume {
            while (true) {
                val windows = receive().windows
                val restored = windowId !in windows && windows.values.any {
                    it.x == resized.x && it.y == resized.y && it.width == resized.width && it.height == resized.height
                }
                if (restored) break
            }
        }
    }
}
