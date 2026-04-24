/*
 * Copyright 2024-2026 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.jvm

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.info
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.SemanticTreeResult
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.SemanticTreeRequest
import java.awt.Window
import javax.accessibility.Accessible

private val logger = createLogger()

internal fun handleSemanticTreeRequest(request: SemanticTreeRequest, window: Window): SemanticTreeResult {
    logger.info("Capturing semantic tree: '${request.messageId}'")
    val rootNode = findRootSemanticsNode(window)
    val json = if (rootNode != null) buildSemanticTreeJson(rootNode)
    else """{"error":"No semantic owners available"}"""
    return SemanticTreeResult(json)
}

/**
 * Walks the Java accessibility tree to find the root [SemanticsNode].
 *
 * Compose Desktop wraps each [SemanticsNode] in an internal `ComposeAccessible`
 * class that has a public `getSemanticsNode()` method.  Once any such node is
 * found we walk up via [SemanticsNode.parent] until we reach the root.
 */
private fun findRootSemanticsNode(accessible: Accessible, depth: Int = 0): SemanticsNode? {
    if (depth > 8) return null
    val node = accessible.getSemanticsNodeOrNull()
    if (node != null) {
        // Walk up to the composition root
        var root = node
        while (!root!!.isRoot) {
            root = root!!.parent ?: break
        }
        return root
    }
    val ctx = accessible.accessibleContext ?: return null
    for (i in 0 until ctx.accessibleChildrenCount) {
        val child = ctx.getAccessibleChild(i) ?: continue
        findRootSemanticsNode(child, depth + 1)?.let { return it }
    }
    return null
}

/**
 * Calls the public `getSemanticsNode()` method declared on Compose Desktop's
 * `ComposeAccessible` (package `androidx.compose.ui.platform.a11y`).
 * Returns null for any plain AWT/Swing accessible that does not carry a Compose node.
 */
private fun Accessible.getSemanticsNodeOrNull(): SemanticsNode? {
    return try {
        // getMethod() searches public methods — no setAccessible needed.
        val method = javaClass.getMethod("getSemanticsNode")
        method.invoke(this) as? SemanticsNode
    } catch (_: NoSuchMethodException) {
        null
    } catch (_: Exception) {
        null
    }
}

internal fun buildSemanticTreeJson(rootNode: SemanticsNode): String =
    buildString { JsonBuilder(this).appendSemanticNode(rootNode) }

// ── JSON helpers ─────────────────────────────────────────────────────────────

/** Builds a single JSON object into [sb], tracking comma-separation automatically. */
private class JsonBuilder(private val sb: StringBuilder) {
    private var needsComma = false
    fun comma() { if (needsComma) sb.append(','); needsComma = true }
    fun str(key: String, value: String) { comma(); sb.append('"').append(key).append("\":\"").append(jsonEscape(value)).append('"') }
    fun raw(key: String, value: String) { comma(); sb.append('"').append(key).append("\":").append(value) }
    fun append(value: String) { sb.append(value) }
    fun append(value: Char) { sb.append(value) }
    fun nested() = JsonBuilder(sb)

    private fun jsonEscape(value: String): String = buildString {
        for (c in value) when (c) {
            '"'  -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(c)
        }
    }
}

// ── SemanticsNode JSON builder ───────────────────────────────────────────────

// TODO: do we need to handle more semantic properties?
private fun JsonBuilder.appendSemanticNode(node: SemanticsNode) {
    val config = node.config
    append('{')

    raw("id", node.id.toString())

    config.getOrNull(SemanticsProperties.Role)?.let { str("role", it.toString()) }

    config.getOrNull(SemanticsProperties.Text)?.takeIf { it.isNotEmpty() }?.let {
        str("text", it.joinToString(" ") { s -> s.text })
    }
    config.getOrNull(SemanticsProperties.EditableText)?.takeIf { it.text.isNotEmpty() }?.let {
        str("editableText", it.text)
    }
    config.getOrNull(SemanticsProperties.ContentDescription)?.takeIf { it.isNotEmpty() }?.let {
        str("contentDescription", it.joinToString(", "))
    }
    config.getOrNull(SemanticsProperties.TestTag)?.let { str("testTag", it) }
    config.getOrNull(SemanticsProperties.StateDescription)?.let { str("stateDescription", it) }
    config.getOrNull(SemanticsProperties.ToggleableState)?.let { str("toggleableState", it.toString()) }
    config.getOrNull(SemanticsProperties.ProgressBarRangeInfo)?.let { info ->
        raw("progressBar", """{"value":${info.current},"range":[${info.range.start},${info.range.endInclusive}],"steps":${info.steps}}""")
    }
    config.getOrNull(SemanticsProperties.Error)?.let { str("error", it) }
    config.getOrNull(SemanticsProperties.Selected)?.let { raw("selected", it.toString()) }
    config.getOrNull(SemanticsProperties.Focused)?.let { raw("focused", it.toString()) }

    if (config.contains(SemanticsProperties.Disabled)) raw("enabled", "false")
    if (config.contains(SemanticsProperties.Heading)) raw("heading", "true")
    if (config.contains(SemanticsProperties.IsDialog)) raw("isDialog", "true")
    if (config.contains(SemanticsProperties.IsPopup)) raw("isPopup", "true")
    if (config.contains(SemanticsProperties.Password)) raw("password", "true")

    val actions = buildList {
        if (config.contains(SemanticsActions.OnClick)) add("onClick")
        if (config.contains(SemanticsActions.OnLongClick)) add("onLongClick")
    }
    if (actions.isNotEmpty()) raw("actions", actions.joinToString(",", "[", "]") { "\"$it\"" })

    with(node.boundsInWindow) {
        raw("bounds", """{"x":${left.toInt()},"y":${top.toInt()},"width":${(right - left).toInt()},"height":${(bottom - top).toInt()}}""")
    }

    val children = node.children
    if (children.isNotEmpty()) {
        comma()
        append("\"children\":[")
        children.forEachIndexed { i, child ->
            if (i > 0) append(',')
            nested().appendSemanticNode(child)
        }
        append(']')
    }

    append('}')
}
