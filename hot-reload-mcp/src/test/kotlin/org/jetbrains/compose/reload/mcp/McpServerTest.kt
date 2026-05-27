/*
 * Copyright 2024-2026 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
@file:OptIn(kotlin.time.ExperimentalTime::class)

package org.jetbrains.compose.reload.mcp

import io.modelcontextprotocol.kotlin.sdk.client.Client
import kotlinx.coroutines.CoroutineScope
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import org.jetbrains.compose.devtools.api.ReloadCountState
import org.jetbrains.compose.devtools.api.ReloadState
import org.jetbrains.compose.devtools.api.WindowsState
import org.jetbrains.compose.devtools.api.WindowsState.WindowState
import org.jetbrains.compose.reload.core.WindowId
import org.jetbrains.compose.reload.core.awaitOrThrow
import org.jetbrains.compose.reload.core.getOrThrow
import org.jetbrains.compose.reload.orchestration.OrchestrationHandle
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ScreenshotRequest
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ScreenshotResult
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.SemanticTreeResult
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.SemanticTreeRequest
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.UIAction
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.UIActionRequest
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.UIActionResult
import org.jetbrains.compose.reload.orchestration.asFlow
import org.jetbrains.compose.reload.orchestration.connectOrchestrationClient
import org.jetbrains.compose.reload.orchestration.OrchestrationClientRole
import org.jetbrains.compose.reload.orchestration.startOrchestrationServer
import java.io.PipedInputStream
import java.io.PipedOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class McpServerTest {

    private suspend fun CoroutineScope.createMcpClient(orchestration: MutableStateFlow<OrchestrationHandle?>): Client {
        val serverIn = PipedInputStream()
        val clientOut = PipedOutputStream(serverIn)
        val clientIn = PipedInputStream()
        val serverOut = PipedOutputStream(clientIn)

        val serverTransport = StdioServerTransport(
            serverIn.asSource().buffered(),
            serverOut.asSink().buffered()
        )

        val clientTransport = StdioClientTransport(
            clientIn.asSource().buffered(),
            clientOut.asSink().buffered()
        )

        launch { startMcpServer(orchestration, serverTransport) }

        val client = Client(Implementation(name = "test-client", version = "1.0.0"))
        client.connect(clientTransport)
        return client
    }

    /**
     * Polls the `status` tool every 10 ms (real time) until the returned JSON contains
     * [expectedSubstring], or fails after 5 real-time seconds.
     */
    private suspend fun awaitStatus(client: Client, expectedSubstring: String): String {
        val deadline = System.currentTimeMillis() + 5_000L
        while (true) {
            val result = client.callTool("status", emptyMap())
            val text = (result.content.first() as TextContent).text
            if (text.contains(expectedSubstring)) return text
            if (System.currentTimeMillis() > deadline) {
                throw AssertionError("Timed out waiting for status containing '$expectedSubstring'. Last status: $text")
            }
            Thread.sleep(10)
        }
    }


    @Test
    fun `test - status returns disconnected when no app`() = runTest(timeout = 10.seconds) {
        val orchestration = MutableStateFlow<OrchestrationHandle?>(null)
        val client = createMcpClient(orchestration)

        val result = client.callTool("status", emptyMap())
        val text = (result.content.first() as TextContent).text
        assertEquals("""{"connected":false}""", text)
    }

    @Test
    fun `test - status returns connected when app is running`() = runTest(timeout = 10.seconds) {
        val server = startOrchestrationServer()
        try {
            val port = server.port.awaitOrThrow()
            val toolingClient = connectOrchestrationClient(OrchestrationClientRole.Tooling, port).getOrThrow()

            val orchestration = MutableStateFlow<OrchestrationHandle?>(toolingClient)
            val client = createMcpClient(orchestration)

            val result = client.callTool("status", emptyMap())
            val text = (result.content.first() as TextContent).text
            assertEquals("""{"connected":true,"reloadState":"ok","lastError":null,"successfulReloads":0,"failedReloads":0}""", text)
        } finally {
            server.close()
        }
    }

    @Test
    fun `test - take_screenshot returns error when disconnected`() = runTest(timeout = 10.seconds) {
        val orchestration = MutableStateFlow<OrchestrationHandle?>(null)
        val client = createMcpClient(orchestration)

        val result = client.callTool("take_screenshot", emptyMap())
        assertTrue(result.isError == true)
        val text = (result.content.first() as TextContent).text
        assertTrue(text.contains("No application is currently connected"))
    }

    @Test
    fun `test - take_screenshot returns image when connected`() = runTest(timeout = 10.seconds) {
        val server = startOrchestrationServer()
        try {
            val port = server.port.awaitOrThrow()
            val toolingClient = connectOrchestrationClient(OrchestrationClientRole.Tooling, port).getOrThrow()

            // Simulate an app that responds to screenshot requests
            launch {
                val request = server.asFlow().filterIsInstance<ScreenshotRequest>().first()
                server.send(ScreenshotResult(
                    screenshotRequestId = request.messageId,
                    format = "png",
                    data = ByteArray(4) { 0xFF.toByte() }
                ))
            }

            val orchestration = MutableStateFlow<OrchestrationHandle?>(toolingClient)
            val client = createMcpClient(orchestration)

            val result = client.callTool("take_screenshot", emptyMap())
            assertNotEquals(true, result.isError)
            val imageContent = result.content.first()
            assertNotNull(imageContent)
        } finally {
            server.close()
        }
    }

    @Test
    fun `test - get_semantic_tree returns error when disconnected`() = runTest(timeout = 10.seconds) {
        val orchestration = MutableStateFlow<OrchestrationHandle?>(null)
        val client = createMcpClient(orchestration)

        val result = client.callTool("get_semantic_tree", emptyMap())
        assertEquals(result.isError, true)
        val text = (result.content.first() as TextContent).text
        assertTrue(text.contains("No application is currently connected"))
    }

    @Test
    fun `test - get_semantic_tree returns tree when connected`() = runTest(timeout = 10.seconds) {
        val server = startOrchestrationServer()
        try {
            val port = server.port.awaitOrThrow()
            val toolingClient = connectOrchestrationClient(OrchestrationClientRole.Tooling, port).getOrThrow()

            val someJson = "{}"

            // Simulate an app that responds to semantic tree requests
            launch {
                val request = server.asFlow().filterIsInstance<SemanticTreeRequest>().first()
                server.send(SemanticTreeResult(semanticTreeRequestId = request.messageId, tree = someJson))
            }

            val orchestration = MutableStateFlow<OrchestrationHandle?>(toolingClient)
            val client = createMcpClient(orchestration)

            val result = client.callTool("get_semantic_tree", emptyMap())
            assertNotEquals(true, result.isError)
            val text = (result.content.first() as TextContent).text
            assertEquals(someJson, text)
        } finally {
            server.close()
        }
    }

    @Test
    fun `test - click returns error when disconnected`() = runTest(timeout = 10.seconds) {
        val orchestration = MutableStateFlow<OrchestrationHandle?>(null)
        val client = createMcpClient(orchestration)

        val result = client.callTool("click", mapOf("nodeId" to 42))
        assertEquals(true, result.isError)
        val text = (result.content.first() as TextContent).text
        assertTrue(text.contains("No application is currently connected"))
    }

    @Test
    fun `test - click succeeds and forwards nodeId`() = runTest(timeout = 10.seconds) {
        val server = startOrchestrationServer()
        try {
            val port = server.port.awaitOrThrow()
            val toolingClient = connectOrchestrationClient(OrchestrationClientRole.Tooling, port).getOrThrow()

            var receivedNodeId: Int? = null
            var receivedAction: UIAction? = null
            launch {
                val request = server.asFlow().filterIsInstance<UIActionRequest>().first()
                receivedNodeId = request.nodeId
                receivedAction = request.action
                server.send(UIActionResult(uiActionRequestId = request.messageId, isSuccess = true))
            }

            val orchestration = MutableStateFlow<OrchestrationHandle?>(toolingClient)
            val client = createMcpClient(orchestration)

            val result = client.callTool("click", mapOf("nodeId" to 7))
            assertNotEquals(true, result.isError)
            assertEquals(7, receivedNodeId)
            assertEquals(UIAction.Click, receivedAction)
        } finally {
            server.close()
        }
    }

    @Test
    fun `test - click returns error when app reports failure`() = runTest(timeout = 10.seconds) {
        val server = startOrchestrationServer()
        try {
            val port = server.port.awaitOrThrow()
            val toolingClient = connectOrchestrationClient(OrchestrationClientRole.Tooling, port).getOrThrow()

            launch {
                val request = server.asFlow().filterIsInstance<UIActionRequest>().first()
                server.send(
                    UIActionResult(
                        uiActionRequestId = request.messageId,
                        isSuccess = false,
                        errorMessage = "Node 7 not found",
                    )
                )
            }

            val orchestration = MutableStateFlow<OrchestrationHandle?>(toolingClient)
            val client = createMcpClient(orchestration)

            val result = client.callTool("click", mapOf("nodeId" to 7))
            assertEquals(true, result.isError)
            val text = (result.content.first() as TextContent).text
            assertTrue(text.contains("Node 7 not found"))
        } finally {
            server.close()
        }
    }

    @Test
    fun `test - long_click succeeds and dispatches LongClick action`() = runTest(timeout = 10.seconds) {
        val server = startOrchestrationServer()
        try {
            val port = server.port.awaitOrThrow()
            val toolingClient = connectOrchestrationClient(OrchestrationClientRole.Tooling, port).getOrThrow()

            var receivedAction: UIAction? = null
            launch {
                val request = server.asFlow().filterIsInstance<UIActionRequest>().first()
                receivedAction = request.action
                server.send(UIActionResult(uiActionRequestId = request.messageId, isSuccess = true))
            }

            val orchestration = MutableStateFlow<OrchestrationHandle?>(toolingClient)
            val client = createMcpClient(orchestration)

            val result = client.callTool("long_click", mapOf("nodeId" to 3))
            assertNotEquals(true, result.isError)
            assertEquals(UIAction.LongClick, receivedAction)
        } finally {
            server.close()
        }
    }

    @Test
    fun `test - type_text forwards text to app`() = runTest(timeout = 10.seconds) {
        val server = startOrchestrationServer()
        try {
            val port = server.port.awaitOrThrow()
            val toolingClient = connectOrchestrationClient(OrchestrationClientRole.Tooling, port).getOrThrow()

            var receivedAction: UIAction? = null
            launch {
                val request = server.asFlow().filterIsInstance<UIActionRequest>().first()
                receivedAction = request.action
                server.send(UIActionResult(uiActionRequestId = request.messageId, isSuccess = true))
            }

            val orchestration = MutableStateFlow<OrchestrationHandle?>(toolingClient)
            val client = createMcpClient(orchestration)

            val result = client.callTool(
                "type_text",
                mapOf("nodeId" to 11, "text" to "hello world"),
            )
            assertNotEquals(true, result.isError)
            assertEquals(UIAction.SetText("hello world"), receivedAction)
        } finally {
            server.close()
        }
    }

    @Test
    fun `test - scroll forwards deltas to app`() = runTest(timeout = 10.seconds) {
        val server = startOrchestrationServer()
        try {
            val port = server.port.awaitOrThrow()
            val toolingClient = connectOrchestrationClient(OrchestrationClientRole.Tooling, port).getOrThrow()

            var receivedAction: UIAction? = null
            launch {
                val request = server.asFlow().filterIsInstance<UIActionRequest>().first()
                receivedAction = request.action
                server.send(UIActionResult(uiActionRequestId = request.messageId, isSuccess = true))
            }

            val orchestration = MutableStateFlow<OrchestrationHandle?>(toolingClient)
            val client = createMcpClient(orchestration)

            val result = client.callTool(
                "scroll",
                mapOf("nodeId" to 5, "deltaX" to 10.0, "deltaY" to -20.5),
            )
            assertNotEquals(true, result.isError)
            assertEquals(UIAction.ScrollBy(10f, -20.5f), receivedAction)
        } finally {
            server.close()
        }
    }

    @Test
    fun `test - scroll_to_index forwards index to app`() = runTest(timeout = 10.seconds) {
        val server = startOrchestrationServer()
        try {
            val port = server.port.awaitOrThrow()
            val toolingClient = connectOrchestrationClient(OrchestrationClientRole.Tooling, port).getOrThrow()

            var receivedAction: UIAction? = null
            launch {
                val request = server.asFlow().filterIsInstance<UIActionRequest>().first()
                receivedAction = request.action
                server.send(UIActionResult(uiActionRequestId = request.messageId, isSuccess = true))
            }

            val orchestration = MutableStateFlow<OrchestrationHandle?>(toolingClient)
            val client = createMcpClient(orchestration)

            val result = client.callTool(
                "scroll_to_index",
                mapOf("nodeId" to 5, "index" to 42),
            )
            assertNotEquals(true, result.isError)
            assertEquals(UIAction.ScrollToIndex(42), receivedAction)
        } finally {
            server.close()
        }
    }

    @Test
    fun `test - status transitions from connected to disconnected`() = runTest(timeout = 10.seconds) {
        val server = startOrchestrationServer()
        val port = server.port.awaitOrThrow()
        val toolingClient = connectOrchestrationClient(OrchestrationClientRole.Tooling, port).getOrThrow()

        val orchestration = MutableStateFlow<OrchestrationHandle?>(toolingClient)
        val client = createMcpClient(orchestration)

        // Connected
        val result1 = client.callTool("status", emptyMap())
        assertEquals(
            """{"connected":true,"reloadState":"ok","lastError":null,"successfulReloads":0,"failedReloads":0}""",
            (result1.content.first() as TextContent).text
        )

        // Simulate app shutdown
        server.close()
        orchestration.value = null

        // Disconnected
        val result2 = client.callTool("status", emptyMap())
        assertEquals("""{"connected":false}""", (result2.content.first() as TextContent).text)
    }

    @Test
    fun `test - status reflects orchestration state after reconnect`() = runTest(timeout = 10.seconds) {
        val server = startOrchestrationServer()
        try {
            val port = server.port.awaitOrThrow()
            val toolingClient1 = connectOrchestrationClient(OrchestrationClientRole.Tooling, port).getOrThrow()

            val orchestration = MutableStateFlow<OrchestrationHandle?>(toolingClient1)
            val client = createMcpClient(orchestration)

            // Drive the state into a non-default value (failed reload).
            server.update(ReloadState) { ReloadState.Failed(reason = "boom") }
            server.update(ReloadCountState) { ReloadCountState(it.successfulReloads, it.failedReloads + 1) }
            awaitStatus(client, """"reloadState":"failed"""")

            // Disconnect, reset state on the server, then reconnect.
            orchestration.value = null
            server.update(ReloadState) { ReloadState.Ok() }
            server.update(ReloadCountState) { ReloadCountState() }
            val toolingClient2 = connectOrchestrationClient(OrchestrationClientRole.Tooling, port).getOrThrow()
            orchestration.value = toolingClient2

            val text = awaitStatus(client, """"reloadState":"ok"""")
            assertEquals(
                """{"connected":true,"reloadState":"ok","lastError":null,"successfulReloads":0,"failedReloads":0}""",
                text,
            )
        } finally {
            server.close()
        }
    }

    @Test
    fun `test - status shows reloading when ReloadState is Reloading`() = runTest(timeout = 10.seconds) {
        val server = startOrchestrationServer()
        try {
            val port = server.port.awaitOrThrow()
            val toolingClient = connectOrchestrationClient(OrchestrationClientRole.Tooling, port).getOrThrow()

            val orchestration = MutableStateFlow<OrchestrationHandle?>(toolingClient)
            val client = createMcpClient(orchestration)

            server.update(ReloadState) { ReloadState.Reloading() }
            val text = awaitStatus(client, """"reloadState":"reloading"""")
            assertEquals(
                """{"connected":true,"reloadState":"reloading","lastError":null,"successfulReloads":0,"failedReloads":0}""",
                text,
            )
        } finally {
            server.close()
        }
    }

    @Test
    fun `test - status reflects successful reload count`() = runTest(timeout = 10.seconds) {
        val server = startOrchestrationServer()
        try {
            val port = server.port.awaitOrThrow()
            val toolingClient = connectOrchestrationClient(OrchestrationClientRole.Tooling, port).getOrThrow()

            val orchestration = MutableStateFlow<OrchestrationHandle?>(toolingClient)
            val client = createMcpClient(orchestration)

            server.update(ReloadState) { ReloadState.Ok() }
            server.update(ReloadCountState) { ReloadCountState(it.successfulReloads + 1, it.failedReloads) }
            val text = awaitStatus(client, """"successfulReloads":1""")
            assertEquals(
                """{"connected":true,"reloadState":"ok","lastError":null,"successfulReloads":1,"failedReloads":0}""",
                text,
            )
        } finally {
            server.close()
        }
    }

    /**
     * Polls the `list_windows` tool until the returned JSON array contains
     * exactly [expectedCount] entries, or fails after 5 real-time seconds.
     */
    private suspend fun awaitWindows(client: Client, expectedCount: Int): String {
        val deadline = System.currentTimeMillis() + 5_000L
        val expectedPrefix = "[" // distinguish from an error response
        while (true) {
            val result = client.callTool("list_windows", emptyMap())
            val text = (result.content.first() as TextContent).text
            if (text.startsWith(expectedPrefix)) {
                val count = if (text == "[]") 0 else text.count { it == '{' }
                if (count == expectedCount) return text
            }
            if (System.currentTimeMillis() > deadline) {
                throw AssertionError("Timed out waiting for $expectedCount window(s). Last: $text")
            }
            Thread.sleep(10)
        }
    }

    private fun windowState(x: Int, y: Int, width: Int, height: Int): WindowState =
        WindowState(x = x, y = y, width = width, height = height, isAlwaysOnTop = false)

    @Test
    fun `test - list_windows returns error when disconnected`() = runTest(timeout = 10.seconds) {
        val orchestration = MutableStateFlow<OrchestrationHandle?>(null)
        val client = createMcpClient(orchestration)

        val result = client.callTool("list_windows", emptyMap())
        assertEquals(true, result.isError)
        val text = (result.content.first() as TextContent).text
        assertTrue(text.contains("No application is currently connected"))
    }

    @Test
    fun `test - list_windows returns empty array when no windows are registered`() = runTest(timeout = 10.seconds) {
        val server = startOrchestrationServer()
        try {
            val port = server.port.awaitOrThrow()
            val toolingClient = connectOrchestrationClient(OrchestrationClientRole.Tooling, port).getOrThrow()

            val orchestration = MutableStateFlow<OrchestrationHandle?>(toolingClient)
            val client = createMcpClient(orchestration)

            val result = client.callTool("list_windows", emptyMap())
            val text = (result.content.first() as TextContent).text
            assertEquals("[]", text)
        } finally {
            server.close()
        }
    }

    @Test
    fun `test - list_windows returns registered windows in insertion order`() = runTest(timeout = 10.seconds) {
        val server = startOrchestrationServer()
        try {
            val port = server.port.awaitOrThrow()
            val toolingClient = connectOrchestrationClient(OrchestrationClientRole.Tooling, port).getOrThrow()

            val orchestration = MutableStateFlow<OrchestrationHandle?>(toolingClient)
            val client = createMcpClient(orchestration)

            server.update(WindowsState) {
                WindowsState(linkedMapOf(
                    WindowId("w-1") to windowState(x = 10, y = 20, width = 100, height = 200),
                    WindowId("w-2") to windowState(x = 30, y = 40, width = 300, height = 400),
                ))
            }

            val text = awaitWindows(client, expectedCount = 2)
            assertEquals(
                """[{"id":"w-1","x":10,"y":20,"width":100,"height":200},""" +
                    """{"id":"w-2","x":30,"y":40,"width":300,"height":400}]""",
                text,
            )
        } finally {
            server.close()
        }
    }

    @Test
    fun `test - take_screenshot forwards explicit window_id`() = runTest(timeout = 10.seconds) {
        val server = startOrchestrationServer()
        try {
            val port = server.port.awaitOrThrow()
            val toolingClient = connectOrchestrationClient(OrchestrationClientRole.Tooling, port).getOrThrow()

            var receivedWindowId: WindowId? = null
            launch {
                val request = server.asFlow().filterIsInstance<ScreenshotRequest>().first()
                receivedWindowId = request.windowId
                server.send(ScreenshotResult(
                    screenshotRequestId = request.messageId,
                    format = "png",
                    data = ByteArray(1) { 0xFF.toByte() }
                ))
            }

            val orchestration = MutableStateFlow<OrchestrationHandle?>(toolingClient)
            val client = createMcpClient(orchestration)

            server.update(WindowsState) {
                WindowsState(linkedMapOf(
                    WindowId("w-1") to windowState(0, 0, 100, 100),
                    WindowId("w-2") to windowState(0, 0, 200, 200),
                ))
            }
            awaitWindows(client, expectedCount = 2)

            val result = client.callTool("take_screenshot", mapOf("window_id" to "w-2"))
            assertNotEquals(true, result.isError)
            assertEquals(WindowId("w-2"), receivedWindowId)
        } finally {
            server.close()
        }
    }

    @Test
    fun `test - take_screenshot defaults to first registered window`() = runTest(timeout = 10.seconds) {
        val server = startOrchestrationServer()
        try {
            val port = server.port.awaitOrThrow()
            val toolingClient = connectOrchestrationClient(OrchestrationClientRole.Tooling, port).getOrThrow()

            var receivedWindowId: WindowId? = null
            launch {
                val request = server.asFlow().filterIsInstance<ScreenshotRequest>().first()
                receivedWindowId = request.windowId
                server.send(ScreenshotResult(
                    screenshotRequestId = request.messageId,
                    format = "png",
                    data = ByteArray(1) { 0xFF.toByte() }
                ))
            }

            val orchestration = MutableStateFlow<OrchestrationHandle?>(toolingClient)
            val client = createMcpClient(orchestration)

            server.update(WindowsState) {
                WindowsState(linkedMapOf(
                    WindowId("w-1") to windowState(0, 0, 100, 100),
                    WindowId("w-2") to windowState(0, 0, 200, 200),
                ))
            }
            awaitWindows(client, expectedCount = 2)

            val result = client.callTool("take_screenshot", emptyMap())
            assertNotEquals(true, result.isError)
            assertEquals(WindowId("w-1"), receivedWindowId)
        } finally {
            server.close()
        }
    }

    @Test
    fun `test - take_screenshot returns error for unknown window_id`() = runTest(timeout = 10.seconds) {
        val server = startOrchestrationServer()
        try {
            val port = server.port.awaitOrThrow()
            val toolingClient = connectOrchestrationClient(OrchestrationClientRole.Tooling, port).getOrThrow()

            val orchestration = MutableStateFlow<OrchestrationHandle?>(toolingClient)
            val client = createMcpClient(orchestration)

            server.update(WindowsState) {
                WindowsState(linkedMapOf(
                    WindowId("w-1") to windowState(0, 0, 100, 100),
                ))
            }
            awaitWindows(client, expectedCount = 1)

            val result = client.callTool("take_screenshot", mapOf("window_id" to "ghost"))
            assertEquals(true, result.isError)
            val text = (result.content.first() as TextContent).text
            assertTrue(text.contains("Window 'ghost' not found"), "unexpected error message: $text")
        } finally {
            server.close()
        }
    }

    @Test
    fun `test - get_semantic_tree forwards explicit window_id`() = runTest(timeout = 10.seconds) {
        val server = startOrchestrationServer()
        try {
            val port = server.port.awaitOrThrow()
            val toolingClient = connectOrchestrationClient(OrchestrationClientRole.Tooling, port).getOrThrow()

            var receivedWindowId: WindowId? = null
            launch {
                val request = server.asFlow().filterIsInstance<SemanticTreeRequest>().first()
                receivedWindowId = request.windowId
                server.send(SemanticTreeResult(semanticTreeRequestId = request.messageId, tree = "{}"))
            }

            val orchestration = MutableStateFlow<OrchestrationHandle?>(toolingClient)
            val client = createMcpClient(orchestration)

            server.update(WindowsState) {
                WindowsState(linkedMapOf(
                    WindowId("w-1") to windowState(0, 0, 100, 100),
                    WindowId("w-2") to windowState(0, 0, 200, 200),
                ))
            }
            awaitWindows(client, expectedCount = 2)

            val result = client.callTool("get_semantic_tree", mapOf("window_id" to "w-2"))
            assertNotEquals(true, result.isError)
            assertEquals(WindowId("w-2"), receivedWindowId)
        } finally {
            server.close()
        }
    }

    @Test
    fun `test - get_semantic_tree returns error for unknown window_id`() = runTest(timeout = 10.seconds) {
        val server = startOrchestrationServer()
        try {
            val port = server.port.awaitOrThrow()
            val toolingClient = connectOrchestrationClient(OrchestrationClientRole.Tooling, port).getOrThrow()

            val orchestration = MutableStateFlow<OrchestrationHandle?>(toolingClient)
            val client = createMcpClient(orchestration)

            server.update(WindowsState) {
                WindowsState(linkedMapOf(
                    WindowId("w-1") to windowState(0, 0, 100, 100),
                ))
            }
            awaitWindows(client, expectedCount = 1)

            val result = client.callTool("get_semantic_tree", mapOf("window_id" to "ghost"))
            assertEquals(true, result.isError)
            val text = (result.content.first() as TextContent).text
            assertTrue(text.contains("Window 'ghost' not found"), "unexpected error message: $text")
        } finally {
            server.close()
        }
    }

    @Test
    fun `test - click forwards explicit window_id`() = runTest(timeout = 10.seconds) {
        val server = startOrchestrationServer()
        try {
            val port = server.port.awaitOrThrow()
            val toolingClient = connectOrchestrationClient(OrchestrationClientRole.Tooling, port).getOrThrow()

            var receivedWindowId: WindowId? = null
            launch {
                val request = server.asFlow().filterIsInstance<UIActionRequest>().first()
                receivedWindowId = request.windowId
                server.send(UIActionResult(uiActionRequestId = request.messageId, isSuccess = true))
            }

            val orchestration = MutableStateFlow<OrchestrationHandle?>(toolingClient)
            val client = createMcpClient(orchestration)

            server.update(WindowsState) {
                WindowsState(linkedMapOf(
                    WindowId("w-1") to windowState(0, 0, 100, 100),
                    WindowId("w-2") to windowState(0, 0, 200, 200),
                ))
            }
            awaitWindows(client, expectedCount = 2)

            val result = client.callTool("click", mapOf("nodeId" to 7, "window_id" to "w-2"))
            assertNotEquals(true, result.isError)
            assertEquals(WindowId("w-2"), receivedWindowId)
        } finally {
            server.close()
        }
    }

    @Test
    fun `test - click returns error for unknown window_id`() = runTest(timeout = 10.seconds) {
        val server = startOrchestrationServer()
        try {
            val port = server.port.awaitOrThrow()
            val toolingClient = connectOrchestrationClient(OrchestrationClientRole.Tooling, port).getOrThrow()

            val orchestration = MutableStateFlow<OrchestrationHandle?>(toolingClient)
            val client = createMcpClient(orchestration)

            server.update(WindowsState) {
                WindowsState(linkedMapOf(
                    WindowId("w-1") to windowState(0, 0, 100, 100),
                ))
            }
            awaitWindows(client, expectedCount = 1)

            val result = client.callTool("click", mapOf("nodeId" to 7, "window_id" to "ghost"))
            assertEquals(true, result.isError)
            val text = (result.content.first() as TextContent).text
            assertTrue(text.contains("Window 'ghost' not found"), "unexpected error message: $text")
        } finally {
            server.close()
        }
    }

    @Test
    fun `test - status shows failed when ReloadState is Failed`() = runTest(timeout = 10.seconds) {
        val server = startOrchestrationServer()
        try {
            val port = server.port.awaitOrThrow()
            val toolingClient = connectOrchestrationClient(OrchestrationClientRole.Tooling, port).getOrThrow()

            val orchestration = MutableStateFlow<OrchestrationHandle?>(toolingClient)
            val client = createMcpClient(orchestration)

            server.update(ReloadState) { ReloadState.Failed(reason = "Compilation error") }
            server.update(ReloadCountState) { ReloadCountState(it.successfulReloads, it.failedReloads + 1) }
            val text = awaitStatus(client, """"reloadState":"failed"""")
            assertEquals(
                """{"connected":true,"reloadState":"failed","lastError":"Compilation error","successfulReloads":0,"failedReloads":1}""",
                text,
            )
        } finally {
            server.close()
        }
    }
}
