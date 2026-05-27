/*
 * Copyright 2024-2026 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.tests

import kotlinx.coroutines.channels.consume
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.devtools.api.WindowsState
import org.jetbrains.compose.reload.core.State
import org.jetbrains.compose.reload.core.WindowId
import org.jetbrains.compose.reload.core.asChannel
import org.jetbrains.compose.reload.core.withAsyncTrace
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ScreenshotRequest
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ScreenshotResult
import org.jetbrains.compose.reload.orchestration.asFlow
import org.jetbrains.compose.reload.test.gradle.Headless
import org.jetbrains.compose.reload.test.gradle.HotReloadTest
import org.jetbrains.compose.reload.test.gradle.HotReloadTestFixture
import org.jetbrains.compose.reload.test.gradle.initialSourceCode
import org.jetbrains.compose.reload.test.gradle.readImage
import org.jetbrains.compose.reload.utils.HostIntegrationTest
import org.jetbrains.compose.reload.utils.QuickTest
import org.jetbrains.skia.Bitmap
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class MultiWindowScreenshotIntegrationTest {

    private val twoWindowSource = """
        import androidx.compose.foundation.background
        import androidx.compose.foundation.layout.Box
        import androidx.compose.foundation.layout.fillMaxSize
        import androidx.compose.ui.Modifier
        import androidx.compose.ui.graphics.Color
        import androidx.compose.ui.unit.dp
        import androidx.compose.ui.window.*

        fun main() {
            application {
                Window(
                    onCloseRequest = ::exitApplication,
                    state = WindowState(width = 200.dp, height = 100.dp),
                    undecorated = true,
                ) {
                    Box(modifier = Modifier.background(Color.Red).fillMaxSize())
                }
                Window(
                    onCloseRequest = ::exitApplication,
                    state = WindowState(width = 200.dp, height = 100.dp),
                    undecorated = true,
                ) {
                    Box(modifier = Modifier.background(Color.Blue).fillMaxSize())
                }
            }
        }
    """.trimIndent()

    @Headless(false)
    @HostIntegrationTest
    @HotReloadTest
    @QuickTest
    fun `test - screenshot with explicit windowId targets that window`(
        fixture: HotReloadTestFixture
    ) = fixture.runTest {
        assumeTrue(isInteractiveDesktopAvailable(), "Test requires an interactive desktop")
        fixture.launchAckSender()

        val windowsState = fixture.orchestration.states.get(WindowsState)
        fixture initialSourceCode twoWindowSource
        awaitWindows(windowsState, 2)

        // The blue window is registered second by the source above.
        val windowIds = windowsState.value.windows.keys.toList()
        val redWidowsId = windowIds[1]
        val blueWindowId = windowIds[1]

        val request = ScreenshotRequest(windowId = blueWindowId)
        val result = fixture.sendMessage(request) {
            skipToMessage<ScreenshotResult> { it.screenshotRequestId == request.messageId }
        }

        assertTrue(result.isSuccess, "Screenshot failed: ${result.errorMessage}")
        val image = result.data.readImage()
        val color = Bitmap.makeFromImage(image).getColor(image.width / 2, image.height / 2)
        assertDominantChannel(color, expected = Channel.Blue)
    }

    /**
     * At the orchestration level, a `ScreenshotRequest` with `windowId = null` is intentionally
     * broadcast to all registered windows — every window-side handler that sees `windowId == null`
     * responds. The "default to first window" semantic lives one layer up in the MCP server
     * ([`resolveWindowId`][org.jetbrains.compose.reload.mcp]) and is covered by `McpServerTest`.
     * This test pins the wire-level contract so a future refactor that changes the filter
     * accidentally fails loudly.
     */
    @Headless(false)
    @HostIntegrationTest
    @HotReloadTest
    @QuickTest
    fun `test - screenshot with null windowId broadcasts to both windows`(
        fixture: HotReloadTestFixture
    ) = fixture.runTest {
        assumeTrue(isInteractiveDesktopAvailable(), "Test requires an interactive desktop")
        fixture.launchAckSender()

        val windowsState = fixture.orchestration.states.get(WindowsState)
        fixture initialSourceCode twoWindowSource
        awaitWindows(windowsState, 2)

        val request = ScreenshotRequest(windowId = null)
        val results = fixture.runTransaction {
            request.send()
            buildList {
                add(skipToMessage<ScreenshotResult> { it.screenshotRequestId == request.messageId })
                add(skipToMessage<ScreenshotResult> { it.screenshotRequestId == request.messageId })
            }
        }
        assertEquals(2, results.size)
        assertTrue(results.all { it.isSuccess }, "All responses must be successful")
    }

    @Headless(false)
    @HostIntegrationTest
    @HotReloadTest
    @QuickTest
    fun `test - screenshot with unknown windowId is dropped`(
        fixture: HotReloadTestFixture
    ) = fixture.runTest {
        assumeTrue(isInteractiveDesktopAvailable(), "Test requires an interactive desktop")
        fixture.launchAckSender()

        val windowsState = fixture.orchestration.states.get(WindowsState)
        fixture initialSourceCode twoWindowSource
        awaitWindows(windowsState, 2)

        val request = ScreenshotRequest(windowId = WindowId("does-not-exist"))
        fixture.orchestration.send(request)

        val received = withTimeoutOrNull(3.seconds) {
            fixture.orchestration.asFlow()
                .filterIsInstance<ScreenshotResult>()
                .first { it.screenshotRequestId == request.messageId }
        }
        assertNull(received, "Expected no response for unknown windowId, got: $received")
    }

    private enum class Channel { Red, Blue }

    private fun assertDominantChannel(argb: Int, expected: Channel) {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        val ok = when (expected) {
            Channel.Red -> r > 200 && g < 60 && b < 60
            Channel.Blue -> b > 200 && r < 60 && g < 60
        }
        assertTrue(ok, "Expected $expected dominant, got R=$r G=$g B=$b (#${Integer.toHexString(argb)})")
    }
}

private suspend fun awaitWindows(windowsState: State<WindowsState>, count: Int) =
    withAsyncTrace("Await $count windows") {
        windowsState.asChannel().consume {
            while (true) {
                if (receive().windows.size == count) break
            }
        }
    }
