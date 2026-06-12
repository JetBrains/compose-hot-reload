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
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.jetbrains.compose.devtools.api.BuildModeState
import org.jetbrains.compose.devtools.api.ReloadCountState
import org.jetbrains.compose.devtools.api.ReloadState
import org.jetbrains.compose.devtools.api.UIErrorState
import org.jetbrains.compose.devtools.api.WindowsState
import org.jetbrains.compose.reload.DelicateHotReloadApi
import org.jetbrains.compose.reload.core.HOT_RELOAD_VERSION
import org.jetbrains.compose.reload.core.WindowId
import org.jetbrains.compose.reload.core.chrLogFile
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.debug
import org.jetbrains.compose.reload.core.info
import org.jetbrains.compose.reload.core.warn
import org.jetbrains.compose.reload.orchestration.OrchestrationHandle
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.CleanCompositionRequest
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
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.RestartRequest
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.WindowResizeRequest
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.WindowResizeResult
import org.jetbrains.compose.reload.orchestration.asChannel
import org.jetbrains.compose.reload.orchestration.flowOf
import java.io.OutputStream
import java.nio.file.Path
import java.util.Base64
import kotlin.io.path.bufferedReader
import kotlin.io.path.createParentDirectories
import kotlin.io.path.isRegularFile
import kotlin.io.path.writeBytes
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private val logger = createLogger()

internal suspend fun startMcpServer(
    orchestration: StateFlow<OrchestrationHandle?>,
    protocolOut: OutputStream,
    pidFile: Path,
) {
    val transport = StdioServerTransport(
        inputStream = System.`in`.asSource().buffered(),
        outputStream = protocolOut.asSink().buffered()
    )
    startMcpServer(orchestration, transport, pidFile)
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
private val MaxErrorDetailLinesParam = Property("max_error_detail_lines", "integer",
    description = "Maximum number of 'lastErrorDetails' lines to include when a reload failed " +
        "(default $DEFAULT_MAX_ERROR_DETAIL_LINES). When the details are longer, " +
        "'lastErrorDetailsTruncated' reports how many lines were dropped. Use 0 to omit the details " +
        "entirely.")
private val LogLimitParam = Property("limit", "integer",
    description = "Maximum number of most recent log lines to return (default $DEFAULT_LOG_LINES). " +
        "Use 0 to return all available lines.")
private val RestartTimeoutParam = Property("timeout_seconds", "integer",
    description = "Maximum seconds to wait for the application to disconnect and reconnect after the " +
        "restart request (default $DEFAULT_RESTART_TIMEOUT_SECONDS). Keep this at or below your own " +
        "tool-call timeout; if it expires the tool returns 'reconnected':false and you should poll the " +
        "'status' tool until 'connected' is true again.")
private val SaveToParam = Property("save_to", "string",
    description = "Absolute path where the screenshot image should be saved on disk. " +
        "When omitted the screenshot is only returned as inline image data.")

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
internal suspend fun startMcpServer(
    orchestration: StateFlow<OrchestrationHandle?>,
    transport: Transport,
    pidFile: Path,
) {
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
                "When connected, additionally returns reload state (ok/reloading/failed), last error if any " +
                "(with 'lastErrorDetails' carrying additional diagnostic lines such as compiler output when " +
                "a reload failed), and counts of successful and failed reloads since the application started. " +
                "When a window is currently failing to render because of a runtime UI exception (distinct " +
                "from a reload failure), 'uiErrorWindows' lists the affected window ids; call 'get_ui_error' " +
                "for the message and stacktrace. " +
                "Call this before take_screenshot to know if the application is available.",
            inputSchema = toolSchema(MaxErrorDetailLinesParam, required = emptyList())
        ) { request ->
            handleStatus(orchestration, request)
        }

        addTool(
            name = "reload",
            description = "Trigger a hot reload of the running Compose application " +
                "when the application was NOT started with continues build mode (--auto): " +
                "recompiles the project sources and reloads the changed classes into the live application without " +
                "restarting it. Call this after editing source files to apply the changes. " +
                "Returns {\"success\": true, \"reloaded\": true} when changed classes were reloaded, " +
                "{\"success\": true, \"reloaded\": false} when compilation succeeded but nothing changed, " +
                "and an error when compilation or the reload fails. " +
                "If the reload does not finish within 'timeout_seconds', returns " +
                "{\"status\": \"reloading\"} (not an error): the reload is still running, so poll the " +
                "'status' tool until 'reloadState' is no longer 'reloading'. " +
                "Use the 'status' tool first to check if the application is connected and its build mode.",
            inputSchema = toolSchema(ReloadTimeoutParam, required = emptyList())
        ) { request ->
            handleReload(orchestration, request)
        }

        addTool(
            name = "await_reload",
            description = "Await a hot reload when the application was started with continuous build mode (--auto): " +
                "the build recompiles and reloads autonomously, and this tool waits for that reload to finish " +
                "and reports the resulting state. Call this after editing source files to let the autonomous build apply changes. " +
                "Returns {\"success\": true} once the application has come into a healthy (reloaded) state, " +
                "and an error when the build or reload fails. " +
                "If the reload does not finish within 'timeout_seconds', returns " +
                "{\"status\": \"reloading\"} (not an error): still waiting for the build to complete, so poll the " +
                "'status' tool until 'reloadState' is no longer 'reloading'. " +
                "Use the 'status' tool first to check if the application is connected and its build mode.",
            inputSchema = toolSchema(ReloadTimeoutParam, required = emptyList())
        ) { request ->
            handleAwaitReload(orchestration, request)
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
            name = "get_ui_error",
            description = "Get the runtime UI exception currently thrown while a single window renders its " +
                "UI (e.g. an exception in a @Composable). " +
                "Returns a JSON object with 'windowId' and 'hasError'; when 'hasError' is true it also " +
                "contains 'message' and 'stacktrace' (a list of lines, capped by " +
                "'max_error_detail_lines', with 'stacktraceTruncated' reporting how many lines were dropped). " +
                "Defaults to the first registered window; pass 'window_id' to target a specific one " +
                "('status' lists failing windows in 'uiErrorWindows').",
            inputSchema = toolSchema(WindowIdParam, MaxErrorDetailLinesParam, required = emptyList())
        ) { request ->
            handleGetUIError(orchestration, request)
        }

        addTool(
            name = "get_logs",
            description = "Return recent log output from the running Compose application. " +
                "Returns the most recent log lines as plain text, oldest first. Includes the " +
                "application's runtime log messages and critical exceptions; build/reload status is " +
                "reported by the 'reload' and 'status' tools instead. Requires a connected " +
                "application; use the 'status' tool first to check availability.",
            inputSchema = toolSchema(LogLimitParam, required = emptyList())
        ) { request ->
            handleGetLogs(orchestration, pidFile.chrLogFile, request)
        }

        addTool(
            name = "take_screenshot",
            description = "Take a screenshot of the running Compose application window. " +
                "Use the 'status' tool first to check if the application is connected.",
            inputSchema = toolSchema(WindowIdParam, SaveToParam, required = emptyList())
        ) { request ->
            handleTakeScreenshot(orchestration, request)
        }

        addTool(
            name = "get_semantic_tree",
            description = "Get the Compose semantic/accessibility tree of the running application. " +
                "Returns a JSON tree describing the UI component hierarchy with roles, names, " +
                "descriptions, states (enabled, visible, focused, etc.), and bounds. " +
                "A window may have several independent roots: an open Dialog, ModalBottomSheet or " +
                "Popup renders as its own root (flagged with 'isDialog'/'isPopup'). When more than " +
                "one root is present the result is a JSON array of roots; with a single root it is " +
                "that root object. " +
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

        addTool(
            name = "resize_window",
            description = "Resize a Compose application window to the given width and height in pixels. " +
                "Use 'list_windows' to inspect current window sizes.",
            inputSchema = toolSchema(
                Property("width", "integer", description = "New window width in pixels (must be positive)."),
                Property("height", "integer", description = "New window height in pixels (must be positive)."),
                WindowIdParam,
                required = listOf("width", "height"),
            )
        ) { request ->
            handleResizeWindow(orchestration, request)
        }

        addTool(
            name = "restart",
            description = "Restart the running Compose application. " +
                "The current application process shuts down and a new one starts with the same arguments. " +
                "Waits for the new application to reconnect and returns {\"success\":true," +
                "\"reconnected\":true} once it does. " +
                "If reconnection does not happen within 'timeout_seconds', returns " +
                "{\"success\":true,\"reconnected\":false} (not an error): the restart was requested but " +
                "the new process is still starting, so poll the 'status' tool until 'connected' is true. " +
                "Use the 'status' tool first to check if the application is connected.",
            inputSchema = toolSchema(RestartTimeoutParam, required = emptyList())
        ) { request ->
            handleRestart(orchestration, request)
        }

        addTool(
            name = "reset_ui",
            description = "Reset the running Compose application's UI: discard the current " +
                "composition so all `remember`-ed state is dropped and the UI rebuilds from scratch. " +
                "Equivalent to clicking the 'Reset UI' button in the dev tools. " +
                "Use the 'status' tool first to check that the application is connected."
        ) { _ ->
            handleResetUi(orchestration)
        }
    }

    server.createSession(transport)
}

/** Standard message returned by every tool when no application is connected. */
private const val NOT_CONNECTED_MESSAGE =
    "No application is currently connected. Use the 'status' tool to check availability."

/** Builds an error [CallToolResult] carrying [message]. */
private fun errorResult(message: String): CallToolResult =
    CallToolResult(content = listOf(TextContent(message)), isError = true)

/** Builds a success [CallToolResult] carrying [text]. */
private fun textResult(text: String): CallToolResult =
    CallToolResult(content = listOf(TextContent(text)))

/**
 * Runs [body] with the currently connected [OrchestrationHandle], or returns the standard
 * "not connected" error. [toolName] is used only for logging.
 */
private suspend fun withConnection(
    orchestration: StateFlow<OrchestrationHandle?>,
    toolName: String,
    body: suspend (OrchestrationHandle) -> CallToolResult,
): CallToolResult {
    val handle = orchestration.value ?: run {
        logger.info { "$toolName: no application connected" }
        return errorResult(NOT_CONNECTED_MESSAGE)
    }
    return body(handle)
}

/**
 * Resolves the target window for [args] and runs [body] with it, or returns a "window not found"
 * error. Use within a [withConnection] body when other arguments must be validated alongside the
 * window; otherwise prefer [withWindow].
 */
private suspend fun OrchestrationHandle.withResolvedWindow(
    args: JsonObject?,
    body: suspend (WindowId) -> CallToolResult,
): CallToolResult = try {
    body(resolveWindowId(this, args))
} catch (e: WindowIdNotFoundException) {
    errorResult(e.message ?: "Invalid window_id")
}

/**
 * Runs [body] with the connected handle and the resolved target window — the common
 * "connected + single window" case. Returns the standard not-connected / window-not-found error
 * otherwise.
 */
private suspend fun withWindow(
    orchestration: StateFlow<OrchestrationHandle?>,
    toolName: String,
    args: JsonObject?,
    body: suspend (OrchestrationHandle, WindowId) -> CallToolResult,
): CallToolResult = withConnection(orchestration, toolName) { handle ->
    handle.withResolvedWindow(args) { windowId -> body(handle, windowId) }
}

private suspend fun handleStatus(
    orchestration: StateFlow<OrchestrationHandle?>,
    toolRequest: CallToolRequest,
): CallToolResult {
    val handle = orchestration.value
    if (handle == null) {
        logger.debug { "status: not connected" }
        return textResult(buildJsonObject { put("connected", false) }.toString())
    }
    val state = handle.states.get(ReloadState).value
    val count = handle.states.get(ReloadCountState).value
    val buildContinuous = handle.states.get(BuildModeState).value.isContinuous
    val reloadStateStr = when (state) {
        is ReloadState.Ok -> "ok"
        is ReloadState.Reloading -> "reloading"
        is ReloadState.Failed -> "failed"
    }
    logger.debug { "status: connected reloadState=$reloadStateStr" }
    return textResult(buildJsonObject {
            put("connected", true)
            put("buildContinuous", buildContinuous)
            put("reloadState", reloadStateStr)
            put("lastError", (state as? ReloadState.Failed)?.reason)
            val maxDetailLines = (toolRequest.arguments?.get("max_error_detail_lines")?.jsonPrimitive?.intOrNull
                ?: DEFAULT_MAX_ERROR_DETAIL_LINES).coerceAtLeast(0)
            (state as? ReloadState.Failed)?.details
                ?.takeIf { it.isNotEmpty() && maxDetailLines > 0 }
                ?.let { details ->
                    val shown = details.take(maxDetailLines)
                    put("lastErrorDetails", buildJsonArray { shown.forEach { add(it) } })
                    if (details.size > shown.size) {
                        put("lastErrorDetailsTruncated", details.size - shown.size)
                    }
                }
            put("successfulReloads", count.successfulReloads)
            put("failedReloads", count.failedReloads)
            val uiErrorWindows = handle.states.get(UIErrorState).value.errors.keys
            if (uiErrorWindows.isNotEmpty()) {
                put("uiErrorWindows", buildJsonArray { uiErrorWindows.forEach { add(it.value) } })
            }
        }.toString())
}

/**
 * Default upper bound on the number of [ReloadState.Failed.details] lines included in the 'status'
 * response, so a pathological stacktrace or compiler dump can't blow the MCP client's response
 * budget. Overridable per call via the 'max_error_detail_lines' parameter. When details are
 * truncated, 'lastErrorDetailsTruncated' reports how many lines were dropped.
 */
private const val DEFAULT_MAX_ERROR_DETAIL_LINES = 100

/**
 * Default time to wait for a reload to finish before telling the agent to poll 'status'.
 * Overridable per call via the 'timeout_seconds' parameter. Kept within a typical MCP client's
 * 60s request budget; a cold Gradle build may legitimately exceed it, hence the poll fallback.
 */
private const val DEFAULT_RELOAD_TIMEOUT_SECONDS = 60

private suspend fun handleReload(
    orchestration: StateFlow<OrchestrationHandle?>,
    toolRequest: CallToolRequest,
): CallToolResult = withConnection(orchestration, "reload") { handle ->
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

    when (outcome) {
        is ReloadClassesResult -> if (outcome.isSuccess) {
            logger.info { "reload: classes reloaded successfully" }
            textResult("""{"success":true,"reloaded":true}""")
        } else {
            logger.warn("reload: failed: ${outcome.errorMessage}")
            errorResult("Reload failed: ${outcome.errorMessage ?: "unknown error"}")
        }

        is RecompileResult -> if (outcome.exitCode == 0) {
            logger.info { "reload: recompiled successfully, no changed classes to reload" }
            textResult("""{"success":true,"reloaded":false,"message":"Recompiled successfully; no changed classes to reload."}""")
        } else {
            logger.warn("reload: recompilation failed with exit code ${outcome.exitCode}")
            errorResult(
                "Recompilation failed (exit code ${outcome.exitCode ?: "unknown"}). " +
                    "Check the build output for compilation errors."
            )
        }

        else -> {
            logger.info { "reload: still in progress after ${timeoutSeconds}s; instructing client to poll 'status'" }
            textResult(
                """{"status":"reloading","message":"Reload still in progress after ${timeoutSeconds}s. """ +
                    """Poll the 'status' tool until 'reloadState' is no longer 'reloading', then check """ +
                    """'successfulReloads'/'failedReloads' or 'lastError'."}"""
            )
        }
    }
}

private suspend fun handleListWindows(orchestration: StateFlow<OrchestrationHandle?>): CallToolResult =
    withConnection(orchestration, "list_windows") { handle ->
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
        textResult(json.toString())
    }

/**
 * How long to wait for the continuous build to pick up the latest edit (i.e. for [ReloadState] to
 * become [ReloadState.Reloading]) before concluding that nothing changed.
 */
private val WAIT_FOR_RELOAD_TO_START_TIMEOUT = 500.milliseconds

/**
 * Awaits reload: observes the autonomous (continuous-build) reload through [ReloadState] and
 * reports whether the application has come into a healthy state.
 *
 * Returns within [timeoutSeconds], degrading to a 'poll status' response if a reload is still running.
 */
private suspend fun handleAwaitReload(
    orchestration: StateFlow<OrchestrationHandle?>,
    toolRequest: CallToolRequest,
): CallToolResult = withConnection(orchestration, "await_reload") { handle ->
    val timeoutSeconds = (toolRequest.arguments?.get("timeout_seconds")?.jsonPrimitive?.intOrNull
        ?: DEFAULT_RELOAD_TIMEOUT_SECONDS).coerceAtLeast(1)
    val timeout = timeoutSeconds.seconds
    logger.info { "await_reload: observing autonomous reload (timeout ${timeoutSeconds}s)" }

    val stateFlow = handle.states.flowOf(ReloadState)

    // If the application is idle, give the continuous build a brief moment to pick up a pending edit.
    if (handle.states.get(ReloadState).value !is ReloadState.Reloading) {
        val started = withTimeoutOrNull(minOf(timeout, WAIT_FOR_RELOAD_TO_START_TIMEOUT)) {
            stateFlow.filterIsInstance<ReloadState.Reloading>().firstOrNull()
        }
        // Nothing started reloading: report the current (settled) state.
        if (started == null) return@withConnection awaitReloadResult(handle.states.get(ReloadState).value, timeoutSeconds)
    }

    // A reload is in progress: wait for it to finish, bounded by the timeout.
    val reloadState = withTimeoutOrNull(timeout) { stateFlow.firstOrNull { it !is ReloadState.Reloading } }
    return@withConnection awaitReloadResult(reloadState, timeoutSeconds)
}

/**
 * Maps [reloadState] to an `await_reload` result. A `null` or still-[ReloadState.Reloading]
 * [reloadState] means the reload did not finish within the timeout, so the client is asked to poll 'status'.
 */
private fun awaitReloadResult(reloadState: ReloadState?, timeoutSeconds: Int): CallToolResult = when (reloadState) {
    is ReloadState.Ok -> {
        logger.info { "await_reload: application is up to date (--auto mode)" }
        textResult("""{"success":true}""")
    }

    is ReloadState.Failed -> {
        logger.warn("await_reload: reload failed (--auto mode): ${reloadState.reason}")
        errorResult("Reload failed: ${reloadState.reason ?: "unknown error"}")
    }

    else -> {
        logger.info { "await_reload: still in progress after ${timeoutSeconds}s; instructing client to poll 'status'" }
        textResult(
            """{"status":"reloading","message":"Reload still in progress after ${timeoutSeconds}s. """ +
                """Poll the 'status' tool until 'reloadState' is no longer 'reloading', then check 'lastError'."}"""
        )
    }
}

private suspend fun handleGetUIError(
    orchestration: StateFlow<OrchestrationHandle?>,
    toolRequest: CallToolRequest,
): CallToolResult = withWindow(orchestration, "get_ui_error", toolRequest.arguments) { handle, resolvedId ->
    val error = handle.states.get(UIErrorState).value.errors[resolvedId]
    val maxDetailLines = (toolRequest.arguments?.get("max_error_detail_lines")?.jsonPrimitive?.intOrNull
        ?: DEFAULT_MAX_ERROR_DETAIL_LINES).coerceAtLeast(0)

    logger.debug { "get_ui_error: window=$resolvedId hasError=${error != null}" }
    val json = buildJsonObject {
        put("windowId", resolvedId.value)
        if (error == null) {
            put("hasError", false)
        } else {
            put("hasError", true)
            put("message", error.message)
            val shown = error.stacktrace.take(maxDetailLines)
            put("stacktrace", buildJsonArray { shown.forEach { add(it) } })
            if (error.stacktrace.size > shown.size) {
                put("stacktraceTruncated", error.stacktrace.size - shown.size)
            }
        }
    }
    textResult(json.toString())
}

/**
 * Default number of trailing log lines returned by 'get_logs'. Overridable per call via the 'limit'
 * parameter (use 0 to return everything available).
 */
private const val DEFAULT_LOG_LINES = 200

/**
 * Returns recent application log output by tailing the agent's '.chr.log' file.
 * The file is written by the application/agent process,
 * so the returned lines include output emitted before this MCP server attached. Requires a connected
 * application; returns the standard "not connected" error otherwise.
 */
private suspend fun handleGetLogs(
    orchestration: StateFlow<OrchestrationHandle?>,
    logFile: Path,
    request: CallToolRequest,
): CallToolResult = withConnection(orchestration, "get_logs") { _ ->
    val limit = (request.arguments?.get("limit")?.jsonPrimitive?.intOrNull ?: DEFAULT_LOG_LINES)
        .coerceAtLeast(0)
    if (!logFile.isRegularFile()) {
        logger.debug { "get_logs: no log file at $logFile" }
        return@withConnection textResult("No logs available (the application has not produced any logs yet).")
    }
    val lines = tailLines(logFile, if (limit == 0) Int.MAX_VALUE else limit)
    logger.debug { "get_logs: returning ${lines.size} line(s) from $logFile" }
    textResult(lines.joinToString("\n"))
}

/**
 * Reads [file] streaming and retains only the last [maxLines] lines (oldest first).
 */
private fun tailLines(file: Path, maxLines: Int): List<String> {
    val deque = ArrayDeque<String>()
    file.bufferedReader().useLines { lines ->
        lines.forEach { line ->
            deque.addLast(line)
            if (deque.size > maxLines) deque.removeFirst()
        }
    }
    return deque.toList()
}

private suspend fun handleTakeScreenshot(
    orchestration: StateFlow<OrchestrationHandle?>,
    request: CallToolRequest,
): CallToolResult = withWindow(orchestration, "take_screenshot", request.arguments) { handle, resolvedId ->
    val saveTo = (request.arguments?.get(SaveToParam.name) as? JsonPrimitive)?.contentOrNull
        ?.let { Path.of(it) }
    try {
        val screenshotRequest = ScreenshotRequest(windowId = resolvedId)
        logger.info { "take_screenshot: sending request '${screenshotRequest.messageId}'" }
        val screenshot = handle.requestResponse<ScreenshotResult>(screenshotRequest) {
            it.screenshotRequestId == screenshotRequest.messageId
        }
        when {
            screenshot == null -> {
                logger.warn("take_screenshot: timed out waiting for response")
                errorResult("Screenshot timed out: no response from application")
            }
            !screenshot.isSuccess -> {
                logger.warn("take_screenshot: failed: ${screenshot.errorMessage}")
                errorResult("Screenshot failed: ${screenshot.errorMessage ?: "unknown error"}")
            }
            else -> {
                logger.debug { "take_screenshot: received ${screenshot.data.size} bytes (${screenshot.format})" }
                val content = buildList {
                    if (saveTo != null) {
                        saveTo.createParentDirectories()
                        saveTo.writeBytes(screenshot.data)
                        logger.info { "take_screenshot: saved to '$saveTo'" }
                        add(TextContent("Screenshot saved to: $saveTo"))
                    }
                    val base64 = Base64.getEncoder().encodeToString(screenshot.data)
                    add(ImageContent(data = base64, mimeType = "image/${screenshot.format}"))
                }
                CallToolResult(content = content)
            }
        }
    } catch (e: Exception) {
        logger.warn("take_screenshot: exception: ${e.message}")
        errorResult("Screenshot failed: ${e.message}")
    }
}

private suspend fun handleGetSemanticTree(
    orchestration: StateFlow<OrchestrationHandle?>,
    request: CallToolRequest,
): CallToolResult = withWindow(orchestration, "get_semantic_tree", request.arguments) { handle, resolvedId ->
    try {
        val treeRequest = SemanticTreeRequest(windowId = resolvedId)
        logger.info { "get_semantic_tree: sending request '${treeRequest.messageId}'" }
        val semanticTree = handle.requestResponse<SemanticTreeResult>(treeRequest) {
            it.semanticTreeRequestId == treeRequest.messageId
        }
        if (semanticTree == null) {
            logger.warn("get_semantic_tree: timed out waiting for response")
            errorResult("Semantic tree request timed out: no response from application")
        } else {
            logger.debug { "get_semantic_tree: received ${semanticTree.tree.length} chars" }
            textResult(semanticTree.tree)
        }
    } catch (e: Exception) {
        logger.warn("get_semantic_tree: exception: ${e.message}")
        errorResult("Semantic tree request failed: ${e.message}")
    }
}

private suspend fun handleUIAction(
    orchestration: StateFlow<OrchestrationHandle?>,
    request: CallToolRequest,
    actionFactory: (JsonObject?) -> UIAction,
): CallToolResult = withConnection(orchestration, request.name) { handle ->
    val args = request.arguments
    val nodeId = args?.get("nodeId")?.jsonPrimitive?.intOrNull
        ?: return@withConnection errorResult("Missing required parameter 'nodeId' (integer)")

    val action = try {
        actionFactory(args)
    } catch (e: Exception) {
        return@withConnection errorResult(e.message ?: "Invalid arguments")
    }

    handle.withResolvedWindow(args) { resolvedId ->
        try {
            val uiActionRequest = UIActionRequest(nodeId = nodeId, action = action, windowId = resolvedId)
            logger.info { "${request.name}: sending request '${uiActionRequest.messageId}' nodeId=$nodeId action=$action" }
            val actionResult = handle.requestResponse<UIActionResult>(uiActionRequest) {
                it.uiActionRequestId == uiActionRequest.messageId
            }
            when {
                actionResult == null -> {
                    logger.warn("${request.name}: timed out waiting for response")
                    errorResult("UI action timed out: no response from application")
                }
                !actionResult.isSuccess -> {
                    logger.warn("${request.name}: failed: ${actionResult.errorMessage}")
                    errorResult("UI action failed: ${actionResult.errorMessage ?: "unknown error"}")
                }
                else -> {
                    logger.debug { "${request.name}: succeeded" }
                    textResult("""{"success": true}""")
                }
            }
        } catch (e: Exception) {
            logger.warn("${request.name}: exception: ${e.message}")
            errorResult("UI action failed: ${e.message}")
        }
    }
}

private suspend fun handleResizeWindow(
    orchestration: StateFlow<OrchestrationHandle?>,
    request: CallToolRequest,
): CallToolResult = withConnection(orchestration, "resize_window") { handle ->
    val args = request.arguments
    val width = args?.get("width")?.jsonPrimitive?.intOrNull
        ?: return@withConnection errorResult("Missing required parameter 'width' (integer)")
    val height = args["height"]?.jsonPrimitive?.intOrNull
        ?: return@withConnection errorResult("Missing required parameter 'height' (integer)")

    val result = handle.withResolvedWindow(args) { resolvedId ->
        try {
            val resizeRequest = WindowResizeRequest(width = width, height = height, windowId = resolvedId)
            logger.info { "resize_window: sending request '${resizeRequest.messageId}' ${width}x${height}" }
            val response = handle.requestResponse<WindowResizeResult>(resizeRequest) {
                it.windowResizeRequestId == resizeRequest.messageId
            }
            when {
                response == null -> errorResult("Window resize timed out: no response from application")
                !response.isSuccess -> errorResult("Window resize failed: ${response.errorMessage ?: "unknown error"}")
                else -> textResult("""{"success": true}""")
            }
        } catch (e: Exception) {
            errorResult("Window resize failed: ${e.message}")
        }
    }

    val resultText = (result.content.firstOrNull() as? TextContent)?.text
    if (result.isError == true) logger.warn("resize_window: $resultText")
    else logger.debug { "resize_window: $resultText" }
    result
}

/**
 * Default time to wait for the application to disconnect and reconnect after a restart request.
 * Overridable per call via the 'timeout_seconds' parameter. A JVM relaunch can take a while,
 * hence the generous default; when it expires the tool falls back to instructing the agent to poll
 * 'status'.
 */
private const val DEFAULT_RESTART_TIMEOUT_SECONDS = 60

private suspend fun handleRestart(
    orchestration: StateFlow<OrchestrationHandle?>,
    toolRequest: CallToolRequest,
): CallToolResult = withConnection(orchestration, "restart") { handle ->
    val timeoutSeconds = (toolRequest.arguments?.get("timeout_seconds")?.jsonPrimitive?.intOrNull
        ?: DEFAULT_RESTART_TIMEOUT_SECONDS).coerceAtLeast(1)

    logger.info { "restart: sending RestartRequest (timeout ${timeoutSeconds}s)" }
    handle.send(RestartRequest())

    val reconnected = withTimeoutOrNull(timeoutSeconds.seconds) {
        orchestration.first { it != null && it !== handle }
    }

    if (reconnected != null) {
        logger.info { "restart: application reconnected" }
        textResult("""{"success":true,"reconnected":true,"message":"Application restarted and reconnected."}""")
    } else {
        logger.info { "restart: no reconnect after ${timeoutSeconds}s; instructing client to poll 'status'" }
        textResult(
            """{"success":true,"reconnected":false,"message":"Restart requested but the application """ +
                """did not reconnect within ${timeoutSeconds}s. Poll the 'status' tool until """ +
                """'connected' is true."}"""
        )
    }
}

private suspend fun handleResetUi(
    orchestration: StateFlow<OrchestrationHandle?>,
): CallToolResult = withConnection(orchestration, "reset_ui") { handle ->
    logger.info { "reset_ui: sending CleanCompositionRequest" }
    handle.send(CleanCompositionRequest())
    textResult("""{"success":true}""")
}

/** Thrown by [resolveWindowId] when an explicit 'window_id' does not match any registered window. */
private class WindowIdNotFoundException(message: String) : Exception(message)

/**
 * Resolves the optional 'window_id' tool argument to a concrete [WindowId], defaulting to the first
 * registered window when omitted. Throws [WindowIdNotFoundException] if the requested window does not
 * exist, or if no window is registered at all.
 *
 * Note the deliberate divergence from the orchestration protocol: a `null` [WindowId] on requests
 * such as [ScreenshotRequest] is broadcast to *every* window, but the MCP tools are request/response
 * and expect a single window's response, so an omitted id resolves to one concrete window rather than
 * being forwarded as `null`.
 */
private suspend fun resolveWindowId(handle: OrchestrationHandle, args: JsonObject?): WindowId {
    val windows = handle.states.get(WindowsState).value.windows
    val explicit = args?.get("window_id")?.jsonPrimitive?.contentOrNull
        ?: return windows.keys.firstOrNull()
            ?: throw WindowIdNotFoundException("No application window is currently available.")
    val id = WindowId(explicit)
    if (id !in windows) throw WindowIdNotFoundException("Window '$explicit' not found")
    return id
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
