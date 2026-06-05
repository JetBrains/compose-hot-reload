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
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.jetbrains.compose.devtools.api.ReloadCountState
import org.jetbrains.compose.devtools.api.ReloadState
import org.jetbrains.compose.devtools.api.WindowsState
import org.jetbrains.compose.reload.DelicateHotReloadApi
import org.jetbrains.compose.reload.core.HOT_RELOAD_VERSION
import org.jetbrains.compose.reload.core.WindowId
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.debug
import org.jetbrains.compose.reload.core.info
import org.jetbrains.compose.reload.core.warn
import org.jetbrains.compose.reload.orchestration.OrchestrationHandle
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.RecompileRequest
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.RecompileResult
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ReloadClassesRequest
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ReloadClassesResult
import org.jetbrains.compose.reload.orchestration.OrchestrationMessageId
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ScreenshotRequest
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ScreenshotResult
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.SemanticTreeResult
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.SemanticTreeRequest
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.UIAction
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.UIActionRequest
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.UIActionResult
import org.jetbrains.compose.reload.orchestration.asChannel
import java.io.OutputStream
import java.util.Base64
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val logger = createLogger()

internal suspend fun startMcpServer(orchestration: StateFlow<OrchestrationHandle?>, protocolOut: OutputStream) {
    val transport = StdioServerTransport(
        inputStream = System.`in`.asSource().buffered(),
        outputStream = protocolOut.asSink().buffered()
    )
    startMcpServer(orchestration, transport)
}

/** A single input property in a tool's JSON schema. */
private data class Property(val name: String, val type: String, val description: String)

private val NodeId = Property("nodeId", "integer",
    description = "Semantic node id as reported by 'get_semantic_tree'.")
private val ScrollContainerNodeId = Property("nodeId", "integer",
    description = "Semantic node id of the scrollable container as reported by 'get_semantic_tree'.")
private val WindowIdParam = Property("window_id", "string",
    description = "Window ID as returned by 'list_windows'. Defaults to the first registered window.")
private val ReloadTimeoutParam = Property("timeout_seconds", "integer",
    description = "Maximum seconds to wait for the reload to finish before returning a " +
        "'still reloading' response (default $DEFAULT_RELOAD_TIMEOUT_SECONDS). Keep this at or below " +
        "your own tool-call timeout; if it expires, poll the 'status' tool until 'reloadState' is no " +
        "longer 'reloading'.")

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
            name = "reload",
            description = "Trigger a hot reload of the running Compose application: recompiles the " +
                "project sources and reloads the changed classes into the live application without " +
                "restarting it. Call this after editing source files to apply the changes. " +
                "Returns {\"success\": true, \"reloaded\": true} when changed classes were reloaded, " +
                "{\"success\": true, \"reloaded\": false} when compilation succeeded but nothing changed, " +
                "and an error when compilation or the reload fails. " +
                "If the reload does not finish within 'timeout_seconds', returns " +
                "{\"status\": \"reloading\"} (not an error): the reload is still running, so poll the " +
                "'status' tool until 'reloadState' is no longer 'reloading'. " +
                "Use the 'status' tool first to check if the application is connected.",
            inputSchema = toolSchema(ReloadTimeoutParam, required = emptyList())
        ) { request ->
            handleReload(orchestration, request)
        }

        addTool(
            name = "list_windows",
            description = "List all currently registered Compose application windows. " +
                "Returns a JSON array with one entry per window containing 'id', 'title', " +
                "'x', 'y', 'width', and 'height'. 'title' is the window title set by the " +
                "application (empty string when none). Use a window 'id' as the 'window_id' " +
                "parameter on window-targeting tools (take_screenshot, get_semantic_tree, " +
                "click, ...) to target a specific window."
        ) { _ ->
            handleListWindows(orchestration)
        }

        addTool(
            name = "take_screenshot",
            description = "Take a screenshot of the running Compose application window. " +
                "Use the 'status' tool first to check if the application is connected.",
            inputSchema = toolSchema(WindowIdParam, required = emptyList())
        ) { request ->
            handleTakeScreenshot(orchestration, request)
        }

        addTool(
            name = "get_semantic_tree",
            description = "Get the Compose semantic/accessibility tree of the running application. " +
                "Returns a JSON tree describing the UI component hierarchy with roles, names, " +
                "descriptions, states (enabled, visible, focused, etc.), and bounds. " +
                "Use the 'status' tool first to check if the application is connected.",
            inputSchema = toolSchema(WindowIdParam, required = emptyList())
        ) { request ->
            handleGetSemanticTree(orchestration, request)
        }

        addTool(
            name = "click",
            description = "Click a UI element. The element must have 'onClick' in its " +
                "'actions' list as reported by 'get_semantic_tree'.",
            inputSchema = toolSchema(NodeId, WindowIdParam, required = listOf(NodeId.name))
        ) { request ->
            handleUIAction(orchestration, request) { UIAction.Click }
        }

        addTool(
            name = "long_click",
            description = "Long-click (long press) a UI element. The element must have " +
                "'onLongClick' in its 'actions' list as reported by 'get_semantic_tree'.",
            inputSchema = toolSchema(NodeId, WindowIdParam, required = listOf(NodeId.name))
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
                WindowIdParam,
                required = listOf(NodeId.name, "text"),
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
                WindowIdParam,
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
                WindowIdParam,
                required = listOf(NodeId.name, "index"),
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

/**
 * Default time to wait for a reload to finish before telling the agent to poll 'status'.
 * Overridable per call via the 'timeout_seconds' parameter. Kept within a typical MCP client's
 * 60s request budget; a cold Gradle build may legitimately exceed it, hence the poll fallback.
 */
private const val DEFAULT_RELOAD_TIMEOUT_SECONDS = 60

private suspend fun handleReload(
    orchestration: StateFlow<OrchestrationHandle?>,
    toolRequest: CallToolRequest,
): CallToolResult {
    val handle = orchestration.value
        ?: return CallToolResult(
            content = listOf(TextContent("No application is currently connected. Use the 'status' tool to check availability.")),
            isError = true
        ).also { logger.info { "reload: no application connected" } }

    val timeoutSeconds = (toolRequest.arguments?.get("timeout_seconds")?.jsonPrimitive?.intOrNull
        ?: DEFAULT_RELOAD_TIMEOUT_SECONDS).coerceAtLeast(1)

    val request = RecompileRequest()
    logger.info { "reload: sending RecompileRequest '${request.messageId}' (timeout ${timeoutSeconds}s)" }

    /*
    When the recompilation produces changed classes, the recompiler issues a ReloadClassesRequest
    (whose id we don't know up front) and the application answers with a ReloadClassesResult
    carrying that id. We capture the id of the first ReloadClassesRequest that follows our
    RecompileRequest, then match its result exactly - this skips stale results from earlier reloads.
    A build that produced no changed classes never issues a ReloadClassesRequest and is detected by
    the RecompileResult for our request instead (sent only once the build, including any reload,
    finished).
     */
    var reloadRequestId: OrchestrationMessageId? = null
    val outcome = handle.sendAndAwait(request, timeoutSeconds.seconds) { message ->
        when (message) {
            is ReloadClassesRequest -> {
                if (reloadRequestId == null) reloadRequestId = message.messageId
                null
            }
            is ReloadClassesResult ->
                message.takeIf { reloadRequestId != null && it.reloadRequestId == reloadRequestId }
            is RecompileResult ->
                message.takeIf { it.recompileRequestId == request.messageId && reloadRequestId == null }
            else -> null
        }
    }

    return when (outcome) {
        is ReloadClassesResult -> if (outcome.isSuccess) {
            logger.info { "reload: classes reloaded successfully" }
            CallToolResult(content = listOf(TextContent("""{"success":true,"reloaded":true}""")))
        } else {
            logger.warn("reload: failed: ${outcome.errorMessage}")
            CallToolResult(
                content = listOf(TextContent("Reload failed: ${outcome.errorMessage ?: "unknown error"}")),
                isError = true
            )
        }

        is RecompileResult -> if (outcome.exitCode == 0) {
            logger.info { "reload: recompiled successfully, no changed classes to reload" }
            CallToolResult(content = listOf(TextContent(
                """{"success":true,"reloaded":false,"message":"Recompiled successfully; no changed classes to reload."}"""
            )))
        } else {
            logger.warn("reload: recompilation failed with exit code ${outcome.exitCode}")
            CallToolResult(
                content = listOf(TextContent(
                    "Recompilation failed (exit code ${outcome.exitCode ?: "unknown"}). " +
                        "Check the build output for compilation errors."
                )),
                isError = true
            )
        }

        else -> {
            logger.info { "reload: still in progress after ${timeoutSeconds}s; instructing client to poll 'status'" }
            CallToolResult(
                content = listOf(TextContent(
                    """{"status":"reloading","message":"Reload still in progress after ${timeoutSeconds}s. """ +
                        """Poll the 'status' tool until 'reloadState' is no longer 'reloading', then check """ +
                        """'successfulReloads'/'failedReloads' or 'lastError'."}"""
                ))
            )
        }
    }
}

private suspend fun handleListWindows(orchestration: StateFlow<OrchestrationHandle?>): CallToolResult {
    val handle = orchestration.value
        ?: return CallToolResult(
            content = listOf(TextContent("No application is currently connected. Use the 'status' tool to check availability.")),
            isError = true
        ).also { logger.info { "list_windows: no application connected" } }

    val windows = handle.states.get(WindowsState).value.windows
    logger.debug { "list_windows: ${windows.size} window(s)" }
    val json = buildJsonArray {
        windows.forEach { (id, state) ->
            addJsonObject {
                put("id", id.value)
                put("title", state.title)
                put("x", state.x)
                put("y", state.y)
                put("width", state.width)
                put("height", state.height)
            }
        }
    }
    return CallToolResult(content = listOf(TextContent(json.toString())))
}

private suspend fun handleTakeScreenshot(
    orchestration: StateFlow<OrchestrationHandle?>,
    request: CallToolRequest,
): CallToolResult {
    val handle = orchestration.value
        ?: return CallToolResult(
            content = listOf(TextContent("No application is currently connected. Use the 'status' tool to check availability.")),
            isError = true
        ).also { logger.info { "take_screenshot: no application connected" } }

    val resolvedId = try {
        resolveWindowId(handle, request.arguments)
    } catch (e: IllegalArgumentException) {
        return CallToolResult(content = listOf(TextContent(e.message ?: "Invalid window_id")), isError = true)
    }

    return try {
        val request = ScreenshotRequest(windowId = resolvedId)
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

private suspend fun handleGetSemanticTree(
    orchestration: StateFlow<OrchestrationHandle?>,
    request: CallToolRequest,
): CallToolResult {
    val handle = orchestration.value
        ?: return CallToolResult(
            content = listOf(TextContent("No application is currently connected. Use the 'status' tool to check availability.")),
            isError = true
        ).also { logger.info { "get_semantic_tree: no application connected" } }

    val resolvedId = try {
        resolveWindowId(handle, request.arguments)
    } catch (e: IllegalArgumentException) {
        return CallToolResult(content = listOf(TextContent(e.message ?: "Invalid window_id")), isError = true)
    }

    return try {
        val request = SemanticTreeRequest(windowId = resolvedId)
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

    val resolvedId = try {
        resolveWindowId(handle, args)
    } catch (e: IllegalArgumentException) {
        return CallToolResult(content = listOf(TextContent(e.message ?: "Invalid window_id")), isError = true)
    }

    return try {
        val uiActionRequest = UIActionRequest(nodeId = nodeId, action = action, windowId = resolvedId)
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

private suspend fun resolveWindowId(handle: OrchestrationHandle, args: JsonObject?): WindowId? {
    val windows = handle.states.get(WindowsState).value.windows
    val explicit = args?.get("window_id")?.jsonPrimitive?.contentOrNull
    if (explicit != null) {
        val id = WindowId(explicit)
        require(id in windows) { "Window '$explicit' not found" }
        return id
    }
    return windows.keys.firstOrNull()
}

/**
 * Sends [request] over orchestration, then collects the resulting messages until [match] maps one
 * to a non-null value, returning it (or null if none arrives within [timeout]).
 *
 * The response channel is opened *before* [request] is sent, eliminating the race where a result
 * could otherwise arrive before a cold flow starts collecting. [consumeAsFlow] auto-closes the
 * channel once a terminal value is found.
 *
 * [match] is invoked on messages in arrival order, so it may accumulate state across calls
 * (e.g. capturing an id from one message to match it against a later one).
 */
private suspend fun <R> OrchestrationHandle.sendAndAwait(
    request: OrchestrationMessage,
    timeout: Duration,
    match: (OrchestrationMessage) -> R?,
): R? {
    val channel = asChannel()
    return channel.consumeAsFlow().let { flow ->
        send(request)
        withTimeoutOrNull(timeout) { flow.mapNotNull(match).firstOrNull() }
    }
}

/**
 * Sends [request] and awaits the matching response of type [R], or null if none arrives within
 * [timeout]. Thin wrapper over [sendAndAwait] for the common single-response-type case.
 */
private suspend inline fun <reified R : OrchestrationMessage> OrchestrationHandle.requestResponse(
    request: OrchestrationMessage,
    timeout: Duration = 10.seconds,
    crossinline isResponse: (R) -> Boolean,
): R? = sendAndAwait(request, timeout) { message -> (message as? R)?.takeIf(isResponse) }
