/*
 * Copyright 2024-2026 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.tests

import org.jetbrains.compose.devtools.api.WindowsState
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.WindowResizeRequest
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.WindowResizeResult
import org.jetbrains.compose.reload.test.gradle.Headless
import org.jetbrains.compose.reload.test.gradle.HotReloadTest
import org.jetbrains.compose.reload.test.gradle.HotReloadTestFixture
import org.jetbrains.compose.reload.test.gradle.initialSourceCode
import org.jetbrains.compose.reload.utils.HostIntegrationTest
import org.jetbrains.compose.reload.utils.QuickTest
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WindowResizeIntegrationTest {

    @Headless(false)
    @HostIntegrationTest
    @HotReloadTest
    @QuickTest
    fun `test - resize window`(fixture: HotReloadTestFixture) = fixture.runTest {
        assumeTrue(isInteractiveDesktopAvailable(), "Test requires an interactive desktop")
        fixture.launchAckSender()

        val windowsState = fixture.orchestration.states.get(WindowsState)

        fixture initialSourceCode """
            import androidx.compose.foundation.layout.Box
            import androidx.compose.ui.unit.dp
            import androidx.compose.ui.window.*
            import org.jetbrains.compose.reload.test.TestText

            fun main() {
                singleWindowApplication(
                    state = WindowState(width = 400.dp, height = 300.dp),
                    undecorated = true,
                ) {
                    Box { TestText("Resize me") }
                }
            }
            """.trimIndent()

        awaitOneWindow(windowsState)
        val (windowId, before) = windowsState.value.windows.entries.first()

        /*
        Resize by a known delta off the reported size. Working off the reported pixel size
        (rather than absolute target) keeps the assertion correct regardless of display scaling.
         */
        val targetWidth = before.width + 123
        val targetHeight = before.height + 77

        val request = WindowResizeRequest(width = targetWidth, height = targetHeight, windowId = windowId)
        val result = fixture.sendMessage(request) {
            skipToMessage<WindowResizeResult> { it.windowResizeRequestId == request.messageId }
        }
        assertTrue(result.isSuccess, "Resize failed: ${result.errorMessage}")

        awaitWindowSize(windowsState, width = targetWidth, height = targetHeight)
        val after = windowsState.value.windows.values.first()
        assertEquals(targetWidth, after.width)
        assertEquals(targetHeight, after.height)
    }
}
