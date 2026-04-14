/*
 * Copyright 2024-2026 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.mcp

import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import io.modelcontextprotocol.kotlin.sdk.shared.Transport
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ImageContent
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import org.jetbrains.compose.reload.DelicateHotReloadApi
import org.jetbrains.compose.reload.core.HOT_RELOAD_VERSION
import org.jetbrains.compose.reload.orchestration.OrchestrationHandle
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ScreenshotRequest
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ScreenshotResult
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.SemanticTreeResult
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.SemanticTreeRequest
import org.jetbrains.compose.reload.orchestration.asChannel
import java.util.Base64
import kotlin.time.Duration.Companion.seconds

internal suspend fun startMcpServer(orchestration: StateFlow<OrchestrationHandle?>) {
    val transport = StdioServerTransport(
        inputStream = System.`in`.asSource().buffered(),
        outputStream = System.out.asSink().buffered()
    )
    startMcpServer(orchestration, transport)
}

@OptIn(DelicateHotReloadApi::class)
internal suspend fun startMcpServer(orchestration: StateFlow<OrchestrationHandle?>, transport: Transport) {
    val server = Server(
        serverInfo = Implementation(name = "compose-hot-reload", version = HOT_RELOAD_VERSION),
        options = ServerOptions(
            capabilities = ServerCapabilities(
                tools = ServerCapabilities.Tools(listChanged = false)
            )
        )
    ) {
        addTool(
            name = "status",
            description = "Check whether a Compose application is currently connected. " +
                "Returns {\"connected\": true} if the application is running and connected, " +
                "or {\"connected\": false} if it is not. " +
                "Call this before take_screenshot to know if the application is available."
        ) { _ ->
            handleStatus(orchestration)
        }

        addTool(
            name = "take_screenshot",
            description = "Take a screenshot of the running Compose application window. " +
                "Use the 'status' tool first to check if the application is connected."
        ) { _ ->
            handleTakeScreenshot(orchestration)
        }

        addTool(
            name = "get_semantic_tree",
            description = "Get the Compose semantic/accessibility tree of the running application. " +
                "Returns a JSON tree describing the UI component hierarchy with roles, names, " +
                "descriptions, states (enabled, visible, focused, etc.), and bounds. " +
                "Use the 'status' tool first to check if the application is connected."
        ) { _ ->
            handleGetSemanticTree(orchestration)
        }
    }

    server.createSession(transport)
}

private fun handleStatus(orchestration: StateFlow<OrchestrationHandle?>): CallToolResult {
    val connected = orchestration.value != null
    return CallToolResult(
        content = listOf(TextContent("""{"connected": $connected}"""))
    )
}

private suspend fun handleTakeScreenshot(orchestration: StateFlow<OrchestrationHandle?>): CallToolResult {
    val handle = orchestration.value
        ?: return CallToolResult(
            content = listOf(TextContent("No application is currently connected. Use the 'status' tool to check availability.")),
            isError = true
        )

    return try {
        val request = ScreenshotRequest()
        // asChannel() registers the listener eagerly before we send the request,
        // avoiding the race condition where the result arrives before
        // the cold asFlow() starts collecting. consumeAsFlow() auto-closes the channel.
        val screenshot = handle.asChannel().consumeAsFlow()
            .filterIsInstance<ScreenshotResult>()
            .let { flow ->
                handle.send(request)
                withTimeoutOrNull(10.seconds) { flow.first { it.screenshotRequestId == request.messageId } }
            }

        if (screenshot == null) {
            return CallToolResult(
                content = listOf(TextContent("Screenshot timed out: no response from application")),
                isError = true
            )
        }

        if (!screenshot.isSuccess) {
            return CallToolResult(
                content = listOf(TextContent("Screenshot failed: ${screenshot.errorMessage ?: "unknown error"}")),
                isError = true
            )
        }

        val base64 = Base64.getEncoder().encodeToString(screenshot.data)
        CallToolResult(
            content = listOf(ImageContent(data = base64, mimeType = "image/${screenshot.format}"))
        )
    } catch (e: Exception) {
        CallToolResult(
            content = listOf(TextContent("Screenshot failed: ${e.message}")),
            isError = true
        )
    }
}

private suspend fun handleGetSemanticTree(orchestration: StateFlow<OrchestrationHandle?>): CallToolResult {
    val handle = orchestration.value
        ?: return CallToolResult(
            content = listOf(TextContent("No application is currently connected. Use the 'status' tool to check availability.")),
            isError = true
        )

    return try {
        val semanticTree = handle.asChannel().consumeAsFlow()
            .filterIsInstance<SemanticTreeResult>()
            .let { flow ->
                handle.send(SemanticTreeRequest())
                withTimeoutOrNull(10.seconds) { flow.first() }
            }

        if (semanticTree == null) {
            return CallToolResult(
                content = listOf(TextContent("Semantic tree request timed out: no response from application")),
                isError = true
            )
        }

        CallToolResult(content = listOf(TextContent(semanticTree.tree)))
    } catch (e: Exception) {
        CallToolResult(
            content = listOf(TextContent("Semantic tree request failed: ${e.message}")),
            isError = true
        )
    }
}
