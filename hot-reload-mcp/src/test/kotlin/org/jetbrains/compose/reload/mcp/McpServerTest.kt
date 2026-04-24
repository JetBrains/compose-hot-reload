/*
 * Copyright 2024-2026 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

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
import org.jetbrains.compose.reload.core.awaitOrThrow
import org.jetbrains.compose.reload.core.getOrThrow
import org.jetbrains.compose.reload.orchestration.OrchestrationHandle
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ScreenshotRequest
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ScreenshotResult
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.SemanticTreeResult
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.SemanticTreeRequest
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

    @Test
    fun `test - status returns disconnected when no app`() = runTest(timeout = 10.seconds) {
        val orchestration = MutableStateFlow<OrchestrationHandle?>(null)
        val client = createMcpClient(orchestration)

        val result = client.callTool("status", emptyMap())
        val text = (result.content.first() as TextContent).text
        assertEquals("""{"connected": false}""", text)
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
            assertEquals("""{"connected": true}""", text)
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
                server.asFlow().filterIsInstance<SemanticTreeRequest>().first()
                server.send(SemanticTreeResult(someJson))
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
    fun `test - status transitions from connected to disconnected`() = runTest(timeout = 10.seconds) {
        val server = startOrchestrationServer()
        val port = server.port.awaitOrThrow()
        val toolingClient = connectOrchestrationClient(OrchestrationClientRole.Tooling, port).getOrThrow()

        val orchestration = MutableStateFlow<OrchestrationHandle?>(toolingClient)
        val client = createMcpClient(orchestration)

        // Connected
        val result1 = client.callTool("status", emptyMap())
        assertEquals("""{"connected": true}""", (result1.content.first() as TextContent).text)

        // Simulate app shutdown
        server.close()
        orchestration.value = null

        // Disconnected
        val result2 = client.callTool("status", emptyMap())
        assertEquals("""{"connected": false}""", (result2.content.first() as TextContent).text)
    }
}
