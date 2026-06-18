/*
 * Copyright 2024-2026 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
@file:OptIn(kotlin.time.ExperimentalTime::class)

package org.jetbrains.compose.reload.mcp

import io.modelcontextprotocol.kotlin.sdk.client.Client
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import io.modelcontextprotocol.kotlin.sdk.types.ImageContent
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import org.jetbrains.compose.devtools.api.ReloadCountState
import org.jetbrains.compose.devtools.api.ReloadState
import org.jetbrains.compose.devtools.api.UIErrorState
import org.jetbrains.compose.devtools.api.WindowsState
import org.jetbrains.compose.devtools.api.WindowsState.WindowState
import org.jetbrains.compose.reload.core.WindowId
import org.jetbrains.compose.reload.core.awaitOrThrow
import org.jetbrains.compose.reload.core.getOrThrow
import org.jetbrains.compose.reload.orchestration.OrchestrationHandle
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.CleanCompositionRequest
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.RecompileRequest
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.RecompileResult
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ReloadClassesRequest
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ReloadClassesResult
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.RestartRequest
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ScreenshotRequest
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ScreenshotResult
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.SemanticTreeResult
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.SemanticTreeRequest
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.UIAction
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.UIActionRequest
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.UIActionResult
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.WindowResizeRequest
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.WindowResizeResult
import org.jetbrains.compose.reload.orchestration.asChannel
import org.jetbrains.compose.reload.orchestration.asFlow
import org.jetbrains.compose.reload.orchestration.connectOrchestrationClient
import org.jetbrains.compose.reload.orchestration.OrchestrationClientRole
import org.jetbrains.compose.reload.orchestration.OrchestrationServer
import org.jetbrains.compose.reload.orchestration.startOrchestrationServer
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.createTempFile
import kotlin.io.path.exists
import kotlin.io.path.readBytes
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class McpServerTest {

    /**
     * The client created during a test. It keeps an MCP server + client read loop alive
     * (blocked on a piped-stream read on a shared dispatcher); closing it after every test
     * frees those threads so the suite does not exhaust the dispatcher's thread pool.
     */
    private var client: Client? = null

    @AfterTest
    fun closeClient() = runBlocking {
        runCatching { client?.close() }
        client = null
    }

    private suspend fun CoroutineScope.createMcpClient(
        orchestration: MutableStateFlow<OrchestrationHandle?>,
        // Defaults to a path that does not exist, modelling "no logs written yet".
        logFile: Path = createTempDirectory().resolve("absent.chr.log"),
    ): Client {
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

        launch { startMcpServer(orchestration, serverTransport, logFile) }

        val client = Client(Implementation(name = "test-client", version = "1.0.0"))
        client.connect(clientTransport)
        this@McpServerTest.client = client
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
        server.use {
            val port = server.port.awaitOrThrow()
            val toolingClient = connectOrchestrationClient(OrchestrationClientRole.Tooling, port).getOrThrow()

            val orchestration = MutableStateFlow<OrchestrationHandle?>(toolingClient)
            val client = createMcpClient(orchestration)

            val result = client.callTool("status", emptyMap())
            val text = (result.content.first() as TextContent).text
            assertEquals("""{"connected":true,"reloadState":"ok","lastError":null,"successfulReloads":0,"failedReloads":0}""", text)
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
        server.use {
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
            registerSingleWindow(server, client)

            val result = client.callTool("take_screenshot", emptyMap())
            assertNotEquals(true, result.isError)
            val imageContent = result.content.first()
            assertNotNull(imageContent)
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
        server.use {
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
            registerSingleWindow(server, client)

            val result = client.callTool("get_semantic_tree", emptyMap())
            assertNotEquals(true, result.isError)
            val text = (result.content.first() as TextContent).text
            assertEquals(someJson, text)
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
        server.use {
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
            registerSingleWindow(server, client)

            val result = client.callTool("click", mapOf("nodeId" to 7))
            assertNotEquals(true, result.isError)
            assertEquals(7, receivedNodeId)
            assertEquals(UIAction.Click, receivedAction)
        }
    }

    @Test
    fun `test - click returns error when app reports failure`() = runTest(timeout = 10.seconds) {
        val server = startOrchestrationServer()
        server.use {
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
            registerSingleWindow(server, client)

            val result = client.callTool("click", mapOf("nodeId" to 7))
            assertEquals(true, result.isError)
            val text = (result.content.first() as TextContent).text
            assertTrue(text.contains("Node 7 not found"))
        }
    }

    @Test
    fun `test - long_click succeeds and dispatches LongClick action`() = runTest(timeout = 10.seconds) {
        val server = startOrchestrationServer()
        server.use {
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
            registerSingleWindow(server, client)

            val result = client.callTool("long_click", mapOf("nodeId" to 3))
            assertNotEquals(true, result.isError)
            assertEquals(UIAction.LongClick, receivedAction)
        }
    }

    @Test
    fun `test - type_text forwards text to app`() = runTest(timeout = 10.seconds) {
        val server = startOrchestrationServer()
        server.use {
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
            registerSingleWindow(server, client)

            val result = client.callTool(
                "type_text",
                mapOf("nodeId" to 11, "text" to "hello world"),
            )
            assertNotEquals(true, result.isError)
            assertEquals(UIAction.SetText("hello world"), receivedAction)
        }
    }

    @Test
    fun `test - scroll forwards deltas to app`() = runTest(timeout = 10.seconds) {
        val server = startOrchestrationServer()
        server.use {
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
            registerSingleWindow(server, client)

            val result = client.callTool(
                "scroll",
                mapOf("nodeId" to 5, "deltaX" to 10.0, "deltaY" to -20.5),
            )
            assertNotEquals(true, result.isError)
            assertEquals(UIAction.ScrollBy(10f, -20.5f), receivedAction)
        }
    }

    @Test
    fun `test - scroll_to_index forwards index to app`() = runTest(timeout = 10.seconds) {
        val server = startOrchestrationServer()
        server.use {
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
            registerSingleWindow(server, client)

            val result = client.callTool(
                "scroll_to_index",
                mapOf("nodeId" to 5, "index" to 42),
            )
            assertNotEquals(true, result.isError)
            assertEquals(UIAction.ScrollToIndex(42), receivedAction)
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
        server.use {
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
        }
    }

    @Test
    fun `test - status shows reloading when ReloadState is Reloading`() = runTest(timeout = 10.seconds) {
        val server = startOrchestrationServer()
        server.use {
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
        }
    }

    @Test
    fun `test - status includes error details when reload failed`() = runTest(timeout = 10.seconds) {
        val server = startOrchestrationServer()
        server.use {
            val port = server.port.awaitOrThrow()
            val toolingClient = connectOrchestrationClient(OrchestrationClientRole.Tooling, port).getOrThrow()

            val orchestration = MutableStateFlow<OrchestrationHandle?>(toolingClient)
            val client = createMcpClient(orchestration)

            server.update(ReloadState) {
                ReloadState.Failed(reason = "boom", details = listOf("line one", "line two"))
            }
            val text = awaitStatus(client, """"reloadState":"failed"""")
            assertEquals(
                """{"connected":true,"reloadState":"failed","lastError":"boom",""" +
                    """"lastErrorDetails":["line one","line two"],""" +
                    """"successfulReloads":0,"failedReloads":0}""",
                text,
            )
        }
    }

    @Test
    fun `test - status truncates long error details`() = runTest(timeout = 10.seconds) {
        val server = startOrchestrationServer()
        server.use {
            val port = server.port.awaitOrThrow()
            val toolingClient = connectOrchestrationClient(OrchestrationClientRole.Tooling, port).getOrThrow()

            val orchestration = MutableStateFlow<OrchestrationHandle?>(toolingClient)
            val client = createMcpClient(orchestration)

            val details = (1..150).map { "line $it" }
            server.update(ReloadState) { ReloadState.Failed(reason = "boom", details = details) }
            val text = awaitStatus(client, """"reloadState":"failed"""")
            // First 100 lines are shown, with the remaining 50 reported as truncated.
            assertTrue(text.contains(""""line 100""""), "Expected the 100th line to be present: $text")
            assertTrue(!text.contains(""""line 101""""), "Expected the 101st line to be dropped: $text")
            assertTrue(text.contains(""""lastErrorDetailsTruncated":50"""), "Expected truncation count: $text")
        }
    }

    @Test
    fun `test - status honors max_error_detail_lines override`() = runTest(timeout = 10.seconds) {
        val server = startOrchestrationServer()
        server.use {
            val port = server.port.awaitOrThrow()
            val toolingClient = connectOrchestrationClient(OrchestrationClientRole.Tooling, port).getOrThrow()

            val orchestration = MutableStateFlow<OrchestrationHandle?>(toolingClient)
            val client = createMcpClient(orchestration)

            server.update(ReloadState) {
                ReloadState.Failed(reason = "boom", details = listOf("a", "b", "c"))
            }
            awaitStatus(client, """"reloadState":"failed"""")

            // Cap at a single line; the remaining two are reported as truncated.
            val result = client.callTool("status", mapOf("max_error_detail_lines" to 1))
            val text = (result.content.first() as TextContent).text
            assertEquals(
                """{"connected":true,"reloadState":"failed","lastError":"boom",""" +
                    """"lastErrorDetails":["a"],"lastErrorDetailsTruncated":2,""" +
                    """"successfulReloads":0,"failedReloads":0}""",
                text,
            )
        }
    }

    @Test
    fun `test - status omits error details when max_error_detail_lines is zero`() = runTest(timeout = 10.seconds) {
        val server = startOrchestrationServer()
        server.use {
            val port = server.port.awaitOrThrow()
            val toolingClient = connectOrchestrationClient(OrchestrationClientRole.Tooling, port).getOrThrow()

            val orchestration = MutableStateFlow<OrchestrationHandle?>(toolingClient)
            val client = createMcpClient(orchestration)

            server.update(ReloadState) {
                ReloadState.Failed(reason = "boom", details = listOf("a", "b"))
            }
            awaitStatus(client, """"reloadState":"failed"""")

            val result = client.callTool("status", mapOf("max_error_detail_lines" to 0))
            val text = (result.content.first() as TextContent).text
            assertEquals(
                """{"connected":true,"reloadState":"failed","lastError":"boom",""" +
                    """"successfulReloads":0,"failedReloads":0}""",
                text,
            )
        }
    }

    @Test
    fun `test - status reflects successful reload count`() = runTest(timeout = 10.seconds) {
        val server = startOrchestrationServer()
        server.use {
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

    private fun windowState(x: Int, y: Int, width: Int, height: Int, title: String? = null): WindowState =
        WindowState(x = x, y = y, width = width, height = height, isAlwaysOnTop = false, title = title)

    /**
     * Registers a single window ('w-1') and waits until the MCP server observes it. Window-targeting
     * tools resolve an omitted 'window_id' to the first registered window, so they require at least
     * one window to be present.
     */
    private suspend fun registerSingleWindow(server: OrchestrationServer, client: Client) {
        server.update(WindowsState) {
            WindowsState(linkedMapOf(WindowId("w-1") to windowState(0, 0, 100, 100)))
        }
        awaitWindows(client, expectedCount = 1)
    }

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
        server.use {
            val port = server.port.awaitOrThrow()
            val toolingClient = connectOrchestrationClient(OrchestrationClientRole.Tooling, port).getOrThrow()

            val orchestration = MutableStateFlow<OrchestrationHandle?>(toolingClient)
            val client = createMcpClient(orchestration)

            val result = client.callTool("list_windows", emptyMap())
            val text = (result.content.first() as TextContent).text
            assertEquals("[]", text)
        }
    }

    @Test
    fun `test - list_windows returns registered windows in insertion order`() = runTest(timeout = 10.seconds) {
        val server = startOrchestrationServer()
        server.use {
            val port = server.port.awaitOrThrow()
            val toolingClient = connectOrchestrationClient(OrchestrationClientRole.Tooling, port).getOrThrow()

            val orchestration = MutableStateFlow<OrchestrationHandle?>(toolingClient)
            val client = createMcpClient(orchestration)

            server.update(WindowsState) {
                WindowsState(linkedMapOf(
                    WindowId("w-1") to windowState(x = 10, y = 20, width = 100, height = 200, title = "Main"),
                    WindowId("w-2") to windowState(x = 30, y = 40, width = 300, height = 400, title = "Settings"),
                ))
            }

            val text = awaitWindows(client, expectedCount = 2)
            assertEquals(
                """[{"id":"w-1","title":"Main","x":10,"y":20,"width":100,"height":200},""" +
                    """{"id":"w-2","title":"Settings","x":30,"y":40,"width":300,"height":400}]""",
                text,
            )
        }
    }

    @Test
    fun `test - list_windows serializes null title as JSON null`() = runTest(timeout = 10.seconds) {
        val server = startOrchestrationServer()
        server.use {
            val port = server.port.awaitOrThrow()
            val toolingClient = connectOrchestrationClient(OrchestrationClientRole.Tooling, port).getOrThrow()

            val orchestration = MutableStateFlow<OrchestrationHandle?>(toolingClient)
            val client = createMcpClient(orchestration)

            server.update(WindowsState) {
                WindowsState(linkedMapOf(
                    WindowId("w-1") to windowState(x = 0, y = 0, width = 100, height = 100, title = null),
                ))
            }

            val text = awaitWindows(client, expectedCount = 1)
            assertEquals(
                """[{"id":"w-1","title":null,"x":0,"y":0,"width":100,"height":100}]""",
                text,
            )
        }
    }

    @Test
    fun `test - list_windows reflects title changes`() = runTest(timeout = 10.seconds) {
        val server = startOrchestrationServer()
        server.use {
            val port = server.port.awaitOrThrow()
            val toolingClient = connectOrchestrationClient(OrchestrationClientRole.Tooling, port).getOrThrow()

            val orchestration = MutableStateFlow<OrchestrationHandle?>(toolingClient)
            val client = createMcpClient(orchestration)

            server.update(WindowsState) {
                WindowsState(linkedMapOf(
                    WindowId("w-1") to windowState(x = 0, y = 0, width = 100, height = 100, title = "Initial"),
                ))
            }
            val before = awaitWindows(client, expectedCount = 1)
            assertTrue(before.contains(""""title":"Initial""""), "Expected initial title, got: $before")

            server.update(WindowsState) { current ->
                val id = WindowId("w-1")
                val old = current.windows.getValue(id)
                WindowsState(linkedMapOf(id to windowState(
                    x = old.x, y = old.y, width = old.width, height = old.height, title = "Renamed",
                )))
            }

            val deadline = System.currentTimeMillis() + 5_000L
            while (true) {
                val text = (client.callTool("list_windows", emptyMap()).content.first() as TextContent).text
                if (text.contains(""""title":"Renamed"""")) break
                if (System.currentTimeMillis() > deadline) {
                    throw AssertionError("Timed out waiting for title rename. Last: $text")
                }
                Thread.sleep(10)
            }
        }
    }

    @Test
    fun `test - take_screenshot forwards explicit window_id`() = runTest(timeout = 10.seconds) {
        val server = startOrchestrationServer()
        server.use {
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
        }
    }

    @Test
    fun `test - take_screenshot defaults to first registered window`() = runTest(timeout = 10.seconds) {
        val server = startOrchestrationServer()
        server.use {
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
        }
    }

    @Test
    fun `test - take_screenshot returns error for unknown window_id`() = runTest(timeout = 10.seconds) {
        val server = startOrchestrationServer()
        server.use {
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
        }
    }

    @Test
    fun `test - take_screenshot saves image to disk and still returns it`() = runTest(timeout = 10.seconds) {
        val server = startOrchestrationServer()
        server.use {
            val port = server.port.awaitOrThrow()
            val toolingClient = connectOrchestrationClient(OrchestrationClientRole.Tooling, port).getOrThrow()

            val imageBytes = ByteArray(8) { it.toByte() }
            launch {
                val request = server.asFlow().filterIsInstance<ScreenshotRequest>().first()
                server.send(ScreenshotResult(
                    screenshotRequestId = request.messageId,
                    format = "png",
                    data = imageBytes,
                ))
            }

            val orchestration = MutableStateFlow<OrchestrationHandle?>(toolingClient)
            val client = createMcpClient(orchestration)
            registerSingleWindow(server, client)

            // A nested path also exercises automatic creation of missing parent directories.
            val target = createTempDirectory().resolve("nested/dir/screenshot.png")
            val result = client.callTool("take_screenshot", mapOf("save_to" to target.toString()))
            assertNotEquals(true, result.isError)

            // The file is written with the raw image bytes.
            assertTrue(target.exists(), "expected screenshot file to be written")
            assertContentEquals(imageBytes, target.readBytes())

            // The result reports the save and still includes the inline image.
            val saveMessage = result.content.filterIsInstance<TextContent>().first().text
            assertTrue(saveMessage.contains(target.toString()), "unexpected save message: $saveMessage")
            assertTrue(result.content.any { it is ImageContent }, "expected inline image content")
        }
    }

    @Test
    fun `test - get_semantic_tree forwards explicit window_id`() = runTest(timeout = 10.seconds) {
        val server = startOrchestrationServer()
        server.use {
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
        }
    }

    @Test
    fun `test - get_semantic_tree returns error for unknown window_id`() = runTest(timeout = 10.seconds) {
        val server = startOrchestrationServer()
        server.use {
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
        }
    }

    @Test
    fun `test - click forwards explicit window_id`() = runTest(timeout = 10.seconds) {
        val server = startOrchestrationServer()
        server.use {
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
        }
    }

    @Test
    fun `test - click returns error for unknown window_id`() = runTest(timeout = 10.seconds) {
        val server = startOrchestrationServer()
        server.use {
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
        }
    }

    @Test
    fun `test - reload returns error when disconnected`() = runTest(timeout = 10.seconds) {
        val orchestration = MutableStateFlow<OrchestrationHandle?>(null)
        val client = createMcpClient(orchestration)

        val result = client.callTool("reload", emptyMap())
        assertEquals(true, result.isError)
        val text = (result.content.first() as TextContent).text
        assertTrue(text.contains("No application is currently connected"))
    }

    @Test
    fun `test - reload reports reloaded when classes are reloaded`() = runTest(timeout = 10.seconds) {
        val server = startOrchestrationServer()
        server.use {
            val port = server.port.awaitOrThrow()
            val toolingClient = connectOrchestrationClient(OrchestrationClientRole.Tooling, port).getOrThrow()

            // Simulate a recompiler that requests a class reload, the app reporting success,
            // then the build result.
            launch {
                val request = server.asFlow().filterIsInstance<RecompileRequest>().first()
                val reloadRequest = ReloadClassesRequest()
                server.send(reloadRequest)
                server.send(ReloadClassesResult(reloadRequestId = reloadRequest.messageId, isSuccess = true))
                server.send(RecompileResult(recompileRequestId = request.messageId, exitCode = 0))
            }

            val orchestration = MutableStateFlow<OrchestrationHandle?>(toolingClient)
            val client = createMcpClient(orchestration)

            val result = client.callTool("reload", emptyMap())
            assertNotEquals(true, result.isError)
            val text = (result.content.first() as TextContent).text
            assertEquals("""{"success":true,"reloaded":true}""", text)
        }
    }

    @Test
    fun `test - reload reports no changes when recompile succeeds without reload`() = runTest(timeout = 10.seconds) {
        val server = startOrchestrationServer()
        server.use {
            val port = server.port.awaitOrThrow()
            val toolingClient = connectOrchestrationClient(OrchestrationClientRole.Tooling, port).getOrThrow()

            launch {
                val request = server.asFlow().filterIsInstance<RecompileRequest>().first()
                server.send(RecompileResult(recompileRequestId = request.messageId, exitCode = 0))
            }

            val orchestration = MutableStateFlow<OrchestrationHandle?>(toolingClient)
            val client = createMcpClient(orchestration)

            val result = client.callTool("reload", emptyMap())
            assertNotEquals(true, result.isError)
            val text = (result.content.first() as TextContent).text
            assertTrue(text.contains(""""reloaded":false"""), "unexpected result: $text")
        }
    }

    @Test
    fun `test - reload returns error when reload fails`() = runTest(timeout = 10.seconds) {
        val server = startOrchestrationServer()
        server.use {
            val port = server.port.awaitOrThrow()
            val toolingClient = connectOrchestrationClient(OrchestrationClientRole.Tooling, port).getOrThrow()

            launch {
                server.asFlow().filterIsInstance<RecompileRequest>().first()
                val reloadRequest = ReloadClassesRequest()
                server.send(reloadRequest)
                server.send(ReloadClassesResult(
                    reloadRequestId = reloadRequest.messageId,
                    isSuccess = false,
                    errorMessage = "Incompatible change",
                ))
            }

            val orchestration = MutableStateFlow<OrchestrationHandle?>(toolingClient)
            val client = createMcpClient(orchestration)

            val result = client.callTool("reload", emptyMap())
            assertEquals(true, result.isError)
            val text = (result.content.first() as TextContent).text
            assertTrue(text.contains("Incompatible change"), "unexpected error message: $text")
        }
    }

    @Test
    fun `test - reload returns error when recompilation fails`() = runTest(timeout = 10.seconds) {
        val server = startOrchestrationServer()
        server.use {
            val port = server.port.awaitOrThrow()
            val toolingClient = connectOrchestrationClient(OrchestrationClientRole.Tooling, port).getOrThrow()

            launch {
                val request = server.asFlow().filterIsInstance<RecompileRequest>().first()
                server.send(RecompileResult(recompileRequestId = request.messageId, exitCode = 1))
            }

            val orchestration = MutableStateFlow<OrchestrationHandle?>(toolingClient)
            val client = createMcpClient(orchestration)

            val result = client.callTool("reload", emptyMap())
            assertEquals(true, result.isError)
            val text = (result.content.first() as TextContent).text
            assertTrue(text.contains("Recompilation failed"), "unexpected error message: $text")
        }
    }

    @Test
    fun `test - reload reports still reloading and asks to poll on timeout`() = runTest(timeout = 10.seconds) {
        val server = startOrchestrationServer()
        server.use {
            val port = server.port.awaitOrThrow()
            val toolingClient = connectOrchestrationClient(OrchestrationClientRole.Tooling, port).getOrThrow()

            // No recompiler responds, so the call must hit the timeout.
            val orchestration = MutableStateFlow<OrchestrationHandle?>(toolingClient)
            val client = createMcpClient(orchestration)

            val result = client.callTool("reload", mapOf("timeout_seconds" to 1))
            assertNotEquals(true, result.isError)
            val text = (result.content.first() as TextContent).text
            assertTrue(text.contains(""""status":"reloading""""), "unexpected result: $text")
            assertTrue(text.contains("status"), "expected a hint to poll 'status': $text")
        }
    }

    @Test
    fun `test - status shows failed when ReloadState is Failed`() = runTest(timeout = 10.seconds) {
        val server = startOrchestrationServer()
        server.use {
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
        }
    }

    @Test
    fun `test - resize_window returns error when disconnected`() = runTest(timeout = 10.seconds) {
        val orchestration = MutableStateFlow<OrchestrationHandle?>(null)
        val client = createMcpClient(orchestration)

        val result = client.callTool("resize_window", mapOf("width" to 800, "height" to 600))
        assertEquals(true, result.isError)
        val text = (result.content.first() as TextContent).text
        assertTrue(text.contains("No application is currently connected"))
    }

    @Test
    fun `test - resize_window forwards width and height`() = runTest(timeout = 10.seconds) {
        val server = startOrchestrationServer()
        server.use {
            val port = server.port.awaitOrThrow()
            val toolingClient = connectOrchestrationClient(OrchestrationClientRole.Tooling, port).getOrThrow()

            var receivedWidth: Int? = null
            var receivedHeight: Int? = null
            launch {
                val request = server.asFlow().filterIsInstance<WindowResizeRequest>().first()
                receivedWidth = request.width
                receivedHeight = request.height
                server.send(WindowResizeResult(windowResizeRequestId = request.messageId, isSuccess = true))
            }

            val orchestration = MutableStateFlow<OrchestrationHandle?>(toolingClient)
            val client = createMcpClient(orchestration)
            registerSingleWindow(server, client)

            val result = client.callTool("resize_window", mapOf("width" to 1024, "height" to 768))
            assertNotEquals(true, result.isError)
            assertEquals(1024, receivedWidth)
            assertEquals(768, receivedHeight)
        }
    }

    @Test
    fun `test - resize_window returns error when app reports failure`() = runTest(timeout = 10.seconds) {
        val server = startOrchestrationServer()
        server.use {
            val port = server.port.awaitOrThrow()
            val toolingClient = connectOrchestrationClient(OrchestrationClientRole.Tooling, port).getOrThrow()

            launch {
                val request = server.asFlow().filterIsInstance<WindowResizeRequest>().first()
                server.send(WindowResizeResult(
                    windowResizeRequestId = request.messageId,
                    isSuccess = false,
                    errorMessage = "Invalid window size",
                ))
            }

            val orchestration = MutableStateFlow<OrchestrationHandle?>(toolingClient)
            val client = createMcpClient(orchestration)
            registerSingleWindow(server, client)

            val result = client.callTool("resize_window", mapOf("width" to -1, "height" to 600))
            assertEquals(true, result.isError)
            val text = (result.content.first() as TextContent).text
            assertTrue(text.contains("Invalid window size"), "unexpected error message: $text")
        }
    }

    /**
     * Polls the `get_ui_error` tool (defaulting to the first registered window) until the returned
     * JSON satisfies [predicate], or fails after 5 real-time seconds. Needed because orchestration
     * state propagates to the tooling client asynchronously.
     */
    private suspend fun awaitUiErrors(client: Client, predicate: (String) -> Boolean): String {
        val deadline = System.currentTimeMillis() + 5_000L
        while (true) {
            val text = (client.callTool("get_ui_error", emptyMap()).content.first() as TextContent).text
            if (predicate(text)) return text
            if (System.currentTimeMillis() > deadline) {
                throw AssertionError("Timed out waiting for get_ui_error. Last: $text")
            }
            Thread.sleep(10)
        }
    }

    @Test
    fun `test - get_ui_error returns error when disconnected`() = runTest(timeout = 10.seconds) {
        val orchestration = MutableStateFlow<OrchestrationHandle?>(null)
        val client = createMcpClient(orchestration)

        val result = client.callTool("get_ui_error", emptyMap())
        assertEquals(true, result.isError)
        val text = (result.content.first() as TextContent).text
        assertTrue(text.contains("No application is currently connected"))
    }

    @Test
    fun `test - get_ui_error reports no error for a healthy window`() = runTest(timeout = 10.seconds) {
        val server = startOrchestrationServer()
        server.use {
            val port = server.port.awaitOrThrow()
            val toolingClient = connectOrchestrationClient(OrchestrationClientRole.Tooling, port).getOrThrow()

            val orchestration = MutableStateFlow<OrchestrationHandle?>(toolingClient)
            val client = createMcpClient(orchestration)
            registerSingleWindow(server, client)

            val result = client.callTool("get_ui_error", emptyMap())
            assertEquals("""{"windowId":"w-1","hasError":false}""", (result.content.first() as TextContent).text)
        }
    }

    @Test
    fun `test - get_ui_error returns the error for the default window`() = runTest(timeout = 10.seconds) {
        val server = startOrchestrationServer()
        server.use {
            val port = server.port.awaitOrThrow()
            val toolingClient = connectOrchestrationClient(OrchestrationClientRole.Tooling, port).getOrThrow()

            val orchestration = MutableStateFlow<OrchestrationHandle?>(toolingClient)
            val client = createMcpClient(orchestration)
            registerSingleWindow(server, client)

            server.update(UIErrorState) {
                UIErrorState(linkedMapOf(
                    WindowId("w-1") to UIErrorState.UIError(
                        message = "boom",
                        stacktrace = listOf("a.b.C.foo(C.kt:1)", "a.b.C.bar(C.kt:2)"),
                    )
                ))
            }

            val text = awaitUiErrors(client) { it.contains(""""hasError":true""") }
            assertEquals(
                """{"windowId":"w-1","hasError":true,"message":"boom",""" +
                    """"stacktrace":["a.b.C.foo(C.kt:1)","a.b.C.bar(C.kt:2)"]}""",
                text,
            )
        }
    }

    @Test
    fun `test - get_ui_error targets explicit window_id`() = runTest(timeout = 10.seconds) {
        val server = startOrchestrationServer()
        server.use {
            val port = server.port.awaitOrThrow()
            val toolingClient = connectOrchestrationClient(OrchestrationClientRole.Tooling, port).getOrThrow()

            val orchestration = MutableStateFlow<OrchestrationHandle?>(toolingClient)
            val client = createMcpClient(orchestration)

            server.update(WindowsState) {
                WindowsState(linkedMapOf(
                    WindowId("w-1") to windowState(0, 0, 100, 100),
                    WindowId("w-2") to windowState(0, 0, 200, 200),
                ))
            }
            awaitWindows(client, expectedCount = 2)

            server.update(UIErrorState) {
                UIErrorState(linkedMapOf(
                    WindowId("w-1") to UIErrorState.UIError("one", emptyList()),
                    WindowId("w-2") to UIErrorState.UIError("two", emptyList()),
                ))
            }
            awaitUiErrors(client) { it.contains(""""hasError":true""") }

            val result = client.callTool("get_ui_error", mapOf("window_id" to "w-2"))
            assertEquals(
                """{"windowId":"w-2","hasError":true,"message":"two","stacktrace":[]}""",
                (result.content.first() as TextContent).text,
            )
        }
    }

    @Test
    fun `test - get_ui_error returns error for unknown window_id`() = runTest(timeout = 10.seconds) {
        val server = startOrchestrationServer()
        server.use {
            val port = server.port.awaitOrThrow()
            val toolingClient = connectOrchestrationClient(OrchestrationClientRole.Tooling, port).getOrThrow()

            val orchestration = MutableStateFlow<OrchestrationHandle?>(toolingClient)
            val client = createMcpClient(orchestration)
            registerSingleWindow(server, client)

            val result = client.callTool("get_ui_error", mapOf("window_id" to "ghost"))
            assertEquals(true, result.isError)
            val text = (result.content.first() as TextContent).text
            assertTrue(text.contains("Window 'ghost' not found"), "unexpected error message: $text")
        }
    }

    @Test
    fun `test - get_ui_error truncates long stacktrace`() = runTest(timeout = 10.seconds) {
        val server = startOrchestrationServer()
        server.use {
            val port = server.port.awaitOrThrow()
            val toolingClient = connectOrchestrationClient(OrchestrationClientRole.Tooling, port).getOrThrow()

            val orchestration = MutableStateFlow<OrchestrationHandle?>(toolingClient)
            val client = createMcpClient(orchestration)
            registerSingleWindow(server, client)

            server.update(UIErrorState) {
                UIErrorState(linkedMapOf(
                    WindowId("w-1") to UIErrorState.UIError(
                        "boom", (1..150).map { "line $it" },
                    )
                ))
            }
            awaitUiErrors(client) { it.contains(""""hasError":true""") }

            val result = client.callTool("get_ui_error", mapOf("max_error_detail_lines" to 2))
            val text = (result.content.first() as TextContent).text
            assertTrue(text.contains(""""stacktrace":["line 1","line 2"]"""), "unexpected stacktrace: $text")
            assertTrue(text.contains(""""stacktraceTruncated":148"""), "expected truncation count: $text")
        }
    }

    @Test
    fun `test - status includes uiErrorWindows when present`() = runTest(timeout = 10.seconds) {
        val server = startOrchestrationServer()
        server.use {
            val port = server.port.awaitOrThrow()
            val toolingClient = connectOrchestrationClient(OrchestrationClientRole.Tooling, port).getOrThrow()

            val orchestration = MutableStateFlow<OrchestrationHandle?>(toolingClient)
            val client = createMcpClient(orchestration)

            server.update(UIErrorState) {
                UIErrorState(linkedMapOf(
                    WindowId("w-1") to UIErrorState.UIError("boom", emptyList()),
                ))
            }
            val text = awaitStatus(client, "uiErrorWindows")
            assertTrue(text.contains(""""uiErrorWindows":["w-1"]"""), "unexpected status: $text")
        }
    }

    @Test
    fun `test - get_logs returns error when disconnected`() = runTest(timeout = 10.seconds) {
        val logFile = createTempFile("chr", ".log")
        logFile.writeText("line 1\nline 2")

        val orchestration = MutableStateFlow<OrchestrationHandle?>(null)
        val client = createMcpClient(orchestration, logFile = logFile)

        val result = client.callTool("get_logs", emptyMap())
        assertEquals(true, result.isError)
        val text = (result.content.first() as TextContent).text
        assertTrue(text.contains("No application is currently connected"))
    }

    @Test
    fun `test - get_logs returns a message when no log file exists`() = runTest(timeout = 10.seconds) {
        val server = startOrchestrationServer()
        server.use {
            val port = server.port.awaitOrThrow()
            val toolingClient = connectOrchestrationClient(OrchestrationClientRole.Tooling, port).getOrThrow()

            val orchestration = MutableStateFlow<OrchestrationHandle?>(toolingClient)
            // Use the default logFile, which points at a non-existent file.
            val client = createMcpClient(orchestration)

            val result = client.callTool("get_logs", emptyMap())
            assertNotEquals(true, result.isError)
            val text = (result.content.first() as TextContent).text
            assertTrue(text.contains("No logs available"), "unexpected result: $text")
        }
    }

    @Test
    fun `test - get_logs returns all lines by default`() = runTest(timeout = 10.seconds) {
        val logFile = createTempFile("chr", ".log")
        logFile.writeText("line 1\nline 2\nline 3")

        val server = startOrchestrationServer()
        server.use {
            val port = server.port.awaitOrThrow()
            val toolingClient = connectOrchestrationClient(OrchestrationClientRole.Tooling, port).getOrThrow()

            val orchestration = MutableStateFlow<OrchestrationHandle?>(toolingClient)
            val client = createMcpClient(orchestration, logFile = logFile)

            val result = client.callTool("get_logs", emptyMap())
            assertNotEquals(true, result.isError)
            val text = (result.content.first() as TextContent).text
            assertEquals("line 1\nline 2\nline 3", text)
        }
    }

    @Test
    fun `test - get_logs honors the limit parameter`() = runTest(timeout = 10.seconds) {
        val logFile = createTempFile("chr", ".log")
        logFile.writeText((1..10).joinToString("\n") { "line $it" })

        val server = startOrchestrationServer()
        server.use {
            val port = server.port.awaitOrThrow()
            val toolingClient = connectOrchestrationClient(OrchestrationClientRole.Tooling, port).getOrThrow()

            val orchestration = MutableStateFlow<OrchestrationHandle?>(toolingClient)
            val client = createMcpClient(orchestration, logFile = logFile)

            val result = client.callTool("get_logs", mapOf("limit" to 3))
            assertNotEquals(true, result.isError)
            val text = (result.content.first() as TextContent).text
            // Returns the most recent lines, oldest first.
            assertEquals("line 8\nline 9\nline 10", text)
        }
    }

    @Test
    fun `test - get_logs returns everything when limit is zero`() = runTest(timeout = 10.seconds) {
        val logFile = createTempFile("chr", ".log")
        val expected = (1..500).joinToString("\n") { "line $it" }
        logFile.writeText(expected)

        val server = startOrchestrationServer()
        server.use {
            val port = server.port.awaitOrThrow()
            val toolingClient = connectOrchestrationClient(OrchestrationClientRole.Tooling, port).getOrThrow()

            val orchestration = MutableStateFlow<OrchestrationHandle?>(toolingClient)
            val client = createMcpClient(orchestration, logFile = logFile)

            val result = client.callTool("get_logs", mapOf("limit" to 0))
            assertNotEquals(true, result.isError)
            val text = (result.content.first() as TextContent).text
            assertEquals(expected, text)
        }
    }

    @Test
    fun `test - restart returns error when disconnected`() = runTest(timeout = 10.seconds) {
        val orchestration = MutableStateFlow<OrchestrationHandle?>(null)
        val client = createMcpClient(orchestration)

        val result = client.callTool("restart", emptyMap())
        assertEquals(true, result.isError)
        val text = (result.content.first() as TextContent).text
        assertTrue(text.contains("No application is currently connected"))
    }

    @Test
    fun `test - restart sends RestartRequest and reports reconnected on new handle`() = runTest(timeout = 10.seconds) {
        val server = startOrchestrationServer()
        server.use {
            val port = server.port.awaitOrThrow()
            val toolingClient = connectOrchestrationClient(OrchestrationClientRole.Tooling, port).getOrThrow()

            val orchestration = MutableStateFlow<OrchestrationHandle?>(toolingClient)
            val client = createMcpClient(orchestration)

            // Subscribe to the broadcast before triggering the restart.
            val messages = server.asChannel()
            launch {
                // The handler broadcast a RestartRequest: simulate the app shutting down
                // (handle → null) and a new app process attaching (handle → new).
                messages.consumeAsFlow().filterIsInstance<RestartRequest>().first()
                orchestration.value = null
                val newToolingClient = connectOrchestrationClient(
                    OrchestrationClientRole.Tooling, port
                ).getOrThrow()
                orchestration.value = newToolingClient
            }

            val result = client.callTool("restart", emptyMap())
            assertNotEquals(true, result.isError)
            val text = (result.content.first() as TextContent).text
            assertTrue(text.contains("\"success\":true"), "got: $text")
            assertTrue(text.contains("\"reconnected\":true"), "got: $text")
        }
    }

    @Test
    fun `test - restart returns reconnected false when no reconnect within timeout`() = runTest(timeout = 10.seconds) {
        val server = startOrchestrationServer()
        server.use {
            val port = server.port.awaitOrThrow()
            val toolingClient = connectOrchestrationClient(OrchestrationClientRole.Tooling, port).getOrThrow()

            val orchestration = MutableStateFlow<OrchestrationHandle?>(toolingClient)
            val client = createMcpClient(orchestration)

            // No coroutine flips the StateFlow — the wait must time out and fall through to
            // the "poll status" fallback instead of erroring.
            val result = client.callTool("restart", mapOf("timeout_seconds" to 1))
            assertNotEquals(true, result.isError)
            val text = (result.content.first() as TextContent).text
            assertTrue(text.contains("\"success\":true"), "got: $text")
            assertTrue(text.contains("\"reconnected\":false"), "got: $text")
        }
    }

    @Test
    fun `test - reset_ui returns error when disconnected`() = runTest(timeout = 10.seconds) {
        val orchestration = MutableStateFlow<OrchestrationHandle?>(null)
        val client = createMcpClient(orchestration)

        val result = client.callTool("reset_ui", emptyMap())
        assertEquals(true, result.isError)
        val text = (result.content.first() as TextContent).text
        assertTrue(text.contains("No application is currently connected"))
    }

    @Test
    fun `test - reset_ui sends CleanCompositionRequest`() = runTest(timeout = 10.seconds) {
        val server = startOrchestrationServer()
        server.use {
            val port = server.port.awaitOrThrow()
            val toolingClient = connectOrchestrationClient(OrchestrationClientRole.Tooling, port).getOrThrow()

            val orchestration = MutableStateFlow<OrchestrationHandle?>(toolingClient)
            val client = createMcpClient(orchestration)

            // Subscribe to the broadcast before triggering reset_ui, otherwise we may miss
            // the CleanCompositionRequest the handler broadcasts.
            val messages = server.asChannel()
            val observed = async {
                messages.consumeAsFlow().filterIsInstance<CleanCompositionRequest>().first()
            }

            val result = client.callTool("reset_ui", emptyMap())
            assertNotEquals(true, result.isError)
            val text = (result.content.first() as TextContent).text
            assertTrue(text.contains("\"success\":true"), "got: $text")
            assertNotNull(observed.await())
        }
    }
}
