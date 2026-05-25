/*
 * Copyright 2024-2026 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.mcp

import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import io.modelcontextprotocol.kotlin.sdk.shared.Transport
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ImageContent
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.jetbrains.compose.devtools.api.ReloadCountState
import org.jetbrains.compose.devtools.api.ReloadState
import org.jetbrains.compose.reload.DelicateHotReloadApi
import org.jetbrains.compose.reload.core.HOT_RELOAD_VERSION
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.debug
import org.jetbrains.compose.reload.core.info
import org.jetbrains.compose.reload.core.warn
import org.jetbrains.compose.reload.orchestration.OrchestrationHandle
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ScreenshotRequest
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ScreenshotResult
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.SemanticTreeResult
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.SemanticTreeRequest
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.UIAction
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.UIActionRequest
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.UIActionResult
import org.jetbrains.compose.reload.orchestration.asChannel
import java.util.Base64
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val logger = createLogger()

internal suspend fun startMcpServer(orchestration: StateFlow<OrchestrationHandle?>) {
    val transport = StdioServerTransport(
        inputStream = System.`in`.asSource().buffered(),
        outputStream = System.out.asSink().buffered()
    )
    startMcpServer(orchestration, transport)
}

/** A single input property in a tool's JSON schema. */
private data class Property(val name: String, val type: String, val description: String)

private val NodeId = Property("nodeId", "integer",
    description = "Semantic node id as reported by 'get_semantic_tree'.")
private val ScrollContainerNodeId = Property("nodeId", "integer",
    description = "Semantic node id of the scrollable container as reported by 'get_semantic_tree'.")

/** Builds a [ToolSchema] from [properties]; by default all of them are required. */
private fun toolSchema(
    vararg properties: Property,
    required: List<String> = properties.map { it.name },
): ToolSchema = ToolSchema(
    properties = buildJsonObject {
        properties.forEach { p ->
            putJsonObject(p.name) {
                put("type", p.type)
                put("description", p.description)
            }
        }
    },
    required = required,
)

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
                "Returns {\"connected\": false} when no application is connected. " +
                "When connected, additionally returns reload state (ok/reloading/failed), last error if any, " +
                "and counts of successful and failed reloads since the application started. " +
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

        addTool(
            name = "click",
            description = "Click a UI element. The element must have 'onClick' in its " +
                "'actions' list as reported by 'get_semantic_tree'.",
            inputSchema = toolSchema(NodeId)
        ) { request ->
            handleUIAction(orchestration, request) { UIAction.Click }
        }

        addTool(
            name = "long_click",
            description = "Long-click (long press) a UI element. The element must have " +
                "'onLongClick' in its 'actions' list as reported by 'get_semantic_tree'.",
            inputSchema = toolSchema(NodeId)
        ) { request ->
            handleUIAction(orchestration, request) { UIAction.LongClick }
        }

        addTool(
            name = "type_text",
            description = "Replace the content of a text field with the given text. " +
                "The element must expose 'editableText' as reported by 'get_semantic_tree'.",
            inputSchema = toolSchema(
                NodeId,
                Property("text", "string",
                    description = "Text to set as the content of the editable field."),
            )
        ) { request ->
            handleUIAction(orchestration, request) { args ->
                val text = (args?.get("text") as? JsonPrimitive)?.content
                    ?: error("Missing required parameter 'text'")
                UIAction.SetText(text)
            }
        }

        addTool(
            name = "scroll",
            description = "Scroll a scrollable container by the given delta in logical pixels. " +
                "Positive deltaX scrolls right; positive deltaY scrolls down. " +
                "The element must support the ScrollBy semantic action.",
            inputSchema = toolSchema(
                ScrollContainerNodeId,
                Property("deltaX", "number",
                    description = "Horizontal scroll delta in logical pixels (positive = right)."),
                Property("deltaY", "number",
                    description = "Vertical scroll delta in logical pixels (positive = down)."),
                required = listOf("nodeId"),
            )
        ) { request ->
            handleUIAction(orchestration, request) { args ->
                val deltaX = args?.get("deltaX")?.jsonPrimitive?.floatOrNull ?: 0f
                val deltaY = args?.get("deltaY")?.jsonPrimitive?.floatOrNull ?: 0f
                UIAction.ScrollBy(deltaX, deltaY)
            }
        }

        addTool(
            name = "scroll_to_index",
            description = "Scroll a scrollable container (e.g. a LazyColumn / LazyRow) so that the item " +
                "at the given index becomes visible. The element must support the ScrollToIndex " +
                "semantic action.",
            inputSchema = toolSchema(
                ScrollContainerNodeId,
                Property("index", "integer",
                    description = "Zero-based index of the item to scroll to."),
            )
        ) { request ->
            handleUIAction(orchestration, request) { args ->
                val index = args?.get("index")?.jsonPrimitive?.intOrNull
                    ?: error("Missing required parameter 'index'")
                UIAction.ScrollToIndex(index)
            }
        }
    }

    server.createSession(transport)
}

private suspend fun handleStatus(orchestration: StateFlow<OrchestrationHandle?>): CallToolResult {
    val handle = orchestration.value
    if (handle == null) {
        logger.debug { "status: not connected" }
        return CallToolResult(
            content = listOf(TextContent(buildJsonObject { put("connected", false) }.toString()))
        )
    }
    val state = handle.states.get(ReloadState).value
    val count = handle.states.get(ReloadCountState).value
    val reloadStateStr = when (state) {
        is ReloadState.Ok -> "ok"
        is ReloadState.Reloading -> "reloading"
        is ReloadState.Failed -> "failed"
    }
    logger.debug { "status: connected reloadState=$reloadStateStr" }
    return CallToolResult(
        content = listOf(TextContent(buildJsonObject {
            put("connected", true)
            put("reloadState", reloadStateStr)
            put("lastError", (state as? ReloadState.Failed)?.reason)
            put("successfulReloads", count.successfulReloads)
            put("failedReloads", count.failedReloads)
        }.toString()))
    )
}

private suspend fun handleTakeScreenshot(orchestration: StateFlow<OrchestrationHandle?>): CallToolResult {
    val handle = orchestration.value
        ?: return CallToolResult(
            content = listOf(TextContent("No application is currently connected. Use the 'status' tool to check availability.")),
            isError = true
        ).also { logger.info { "take_screenshot: no application connected" } }

    return try {
        val request = ScreenshotRequest()
        logger.info { "take_screenshot: sending request '${request.messageId}'" }
        val screenshot = handle.requestResponse<ScreenshotResult>(request) {
            it.screenshotRequestId == request.messageId
        }

        if (screenshot == null) {
            logger.warn("take_screenshot: timed out waiting for response")
            return CallToolResult(
                content = listOf(TextContent("Screenshot timed out: no response from application")),
                isError = true
            )
        }

        if (!screenshot.isSuccess) {
            logger.warn("take_screenshot: failed: ${screenshot.errorMessage}")
            return CallToolResult(
                content = listOf(TextContent("Screenshot failed: ${screenshot.errorMessage ?: "unknown error"}")),
                isError = true
            )
        }

        logger.debug { "take_screenshot: received ${screenshot.data.size} bytes (${screenshot.format})" }
        val base64 = Base64.getEncoder().encodeToString(screenshot.data)
        CallToolResult(
            content = listOf(ImageContent(data = base64, mimeType = "image/${screenshot.format}"))
        )
    } catch (e: Exception) {
        logger.warn("take_screenshot: exception: ${e.message}")
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
        ).also { logger.info { "get_semantic_tree: no application connected" } }

    return try {
        val request = SemanticTreeRequest()
        logger.info { "get_semantic_tree: sending request '${request.messageId}'" }
        val semanticTree = handle.requestResponse<SemanticTreeResult>(request) {
            it.semanticTreeRequestId == request.messageId
        }

        if (semanticTree == null) {
            logger.warn("get_semantic_tree: timed out waiting for response")
            return CallToolResult(
                content = listOf(TextContent("Semantic tree request timed out: no response from application")),
                isError = true
            )
        }

        logger.debug { "get_semantic_tree: received ${semanticTree.tree.length} chars" }
        CallToolResult(content = listOf(TextContent(semanticTree.tree)))
    } catch (e: Exception) {
        logger.warn("get_semantic_tree: exception: ${e.message}")
        CallToolResult(
            content = listOf(TextContent("Semantic tree request failed: ${e.message}")),
            isError = true
        )
    }
}

private suspend fun handleUIAction(
    orchestration: StateFlow<OrchestrationHandle?>,
    request: CallToolRequest,
    actionFactory: (JsonObject?) -> UIAction,
): CallToolResult {
    val handle = orchestration.value
        ?: return CallToolResult(
            content = listOf(TextContent("No application is currently connected. Use the 'status' tool to check availability.")),
            isError = true
        ).also { logger.info { "${request.name}: no application connected" } }

    val args = request.arguments
    val nodeId = args?.get("nodeId")?.jsonPrimitive?.intOrNull
        ?: return CallToolResult(
            content = listOf(TextContent("Missing required parameter 'nodeId' (integer)")),
            isError = true
        )

    val action = try {
        actionFactory(args)
    } catch (e: Exception) {
        return CallToolResult(
            content = listOf(TextContent(e.message ?: "Invalid arguments")),
            isError = true
        )
    }

    return try {
        val uiActionRequest = UIActionRequest(nodeId = nodeId, action = action)
        logger.info { "${request.name}: sending request '${uiActionRequest.messageId}' nodeId=$nodeId action=$action" }
        val actionResult = handle.requestResponse<UIActionResult>(uiActionRequest) {
            it.uiActionRequestId == uiActionRequest.messageId
        }

        if (actionResult == null) {
            logger.warn("${request.name}: timed out waiting for response")
            return CallToolResult(
                content = listOf(TextContent("UI action timed out: no response from application")),
                isError = true
            )
        }

        if (!actionResult.isSuccess) {
            logger.warn("${request.name}: failed: ${actionResult.errorMessage}")
            return CallToolResult(
                content = listOf(TextContent("UI action failed: ${actionResult.errorMessage ?: "unknown error"}")),
                isError = true
            )
        }

        logger.debug { "${request.name}: succeeded" }
        CallToolResult(content = listOf(TextContent("""{"success": true}""")))
    } catch (e: Exception) {
        logger.warn("${request.name}: exception: ${e.message}")
        CallToolResult(
            content = listOf(TextContent("UI action failed: ${e.message}")),
            isError = true
        )
    }
}

/**
 * Sends [request] over orchestration and awaits the matching response of type [R],
 * or returns null if no response arrives within [timeout].
 *
 * The response channel is opened *before* [request] is sent, eliminating the race
 * where the result could otherwise arrive before a cold flow starts collecting.
 * [consumeAsFlow] auto-closes the channel once the first match is found.
 */
private suspend inline fun <reified R : OrchestrationMessage> OrchestrationHandle.requestResponse(
    request: OrchestrationMessage,
    timeout: Duration = 10.seconds,
    crossinline isResponse: (R) -> Boolean,
): R? {
    val channel = asChannel()
    return channel.consumeAsFlow()
        .filterIsInstance<R>()
        .let { flow ->
            send(request)
            withTimeoutOrNull(timeout) { flow.first { isResponse(it) } }
        }
}
