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
    val roots = findAllRootSemanticsNodes(window)
    return SemanticTreeResult(semanticTreeRequestId = request.messageId, tree = renderSemanticForest(roots))
}

/**
 * Maximum recursion depth when walking the Java accessibility tree searching for a Compose
 * [SemanticsNode]. The Compose root is typically only a few levels below the AWT [Window],
 * so a small bound is sufficient and guards against accidentally walking unrelated AWT/Swing
 * accessible subtrees. 8 levels feel like good enough.
 */
private const val MAX_ACCESSIBILITY_TREE_SEARCH_DEPTH = 8

/**
 * Walks the Java accessibility tree to find every root [SemanticsNode] in the [accessible] window.
 *
 * Compose Desktop renders each `Dialog` / `ModalBottomSheet` / `Popup` in its own owner — a
 * separate semantics root — within the same window. The accessibility integration exposes one
 * root per owner as a sibling accessible, so we collect every distinct Compose root.
 *
 * Compose Desktop wraps each [SemanticsNode] in an internal `ComposeAccessible` class that has a
 * public `getSemanticsNode()` method. Once such a node is found we walk up via [SemanticsNode.parent]
 * to its root and stop descending: everything below belongs to the same owner.
 */
internal fun findAllRootSemanticsNodes(accessible: Accessible): List<SemanticsNode> {
    val roots = LinkedHashMap<Int, SemanticsNode>()
    collectRootSemanticsNodes(accessible, depth = 0, roots = roots)
    return roots.values.toList()
}

private fun collectRootSemanticsNodes(
    accessible: Accessible,
    depth: Int,
    roots: LinkedHashMap<Int, SemanticsNode>,
) {
    if (depth > MAX_ACCESSIBILITY_TREE_SEARCH_DEPTH) return

    val node = accessible.getSemanticsNodeOrNull()
    if (node != null) {
        var root: SemanticsNode = node
        while (!root.isRoot) {
            root = root.parent ?: break
        }
        roots.putIfAbsent(root.id, root)
        /* All accessibles below this one share the same owner/root, so stop descending. */
        return
    }

    val ctx = accessible.accessibleContext ?: return
    for (i in 0 until ctx.accessibleChildrenCount) {
        val child = ctx.getAccessibleChild(i) ?: continue
        collectRootSemanticsNodes(child, depth + 1, roots)
    }
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

/** Renders all captured semantic [roots] (main window plus any Dialog/ModalBottomSheet/Popup). */
internal fun renderSemanticForest(roots: List<SemanticsNode>): String =
    joinSemanticForest(roots.map { buildSemanticTreeJson(it) })

/**
 * Combines already-rendered root JSON objects into the result:
 *  - no roots         -> an error object (no owners available yet)
 *  - exactly one root -> the single root object as-is (the common case, unchanged shape)
 *  - multiple roots   -> a JSON array of roots (e.g. main window plus open overlays)
 */
internal fun joinSemanticForest(rendered: List<String>): String = when (rendered.size) {
    0 -> """{"error":"No semantic owners available"}"""
    1 -> rendered[0]
    else -> rendered.joinToString(",", "[", "]")
}

// ── JSON helpers ─────────────────────────────────────────────────────────────

/** Builds a single JSON object into [sb], tracking comma-separation automatically. */
internal class JsonBuilder(private val sb: StringBuilder) {
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
