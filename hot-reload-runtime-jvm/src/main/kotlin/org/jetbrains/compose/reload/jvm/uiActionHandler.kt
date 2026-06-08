/*
 * Copyright 2024-2026 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.jvm

import androidx.compose.ui.semantics.AccessibilityAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.AnnotatedString
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.info
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.UIAction
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.UIActionRequest
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.UIActionResult
import java.awt.Window

private val logger = createLogger()

internal fun handleUIActionRequest(request: UIActionRequest, window: Window): UIActionResult {
    return handleUIActionRequest(request, findAllRootSemanticsNodes(window))
}

internal fun handleUIActionRequest(request: UIActionRequest, roots: List<SemanticsNode>): UIActionResult {
    logger.info("Handling UI action: '${request.messageId}' nodeId=${request.nodeId} action=${request.action}")
    return try {
        dispatchUIAction(request, roots)
    } catch (e: Exception) {
        logger.info("UI action failed: ${e.message}")
        UIActionResult(
            uiActionRequestId = request.messageId,
            isSuccess = false,
            errorMessage = "UI action failed: ${e.message}",
        )
    }
}

private fun dispatchUIAction(request: UIActionRequest, roots: List<SemanticsNode>): UIActionResult {
    if (roots.isEmpty()) return UIActionResult(
        uiActionRequestId = request.messageId,
        isSuccess = false,
        errorMessage = "No semantic owners available",
    )

    /* Search across all roots so actions also reach nodes inside a Dialog/ModalBottomSheet/Popup. */
    val node = findSemanticsNodeById(roots, request.nodeId)
        ?: return UIActionResult(
            uiActionRequestId = request.messageId,
            isSuccess = false,
            errorMessage = "Node ${request.nodeId} not found",
        )

    return when (val action = request.action) {
        is UIAction.Click -> invokeNoArgAction(
            request, node, SemanticsActions.OnClick, "onClick"
        )
        is UIAction.LongClick -> invokeNoArgAction(
            request, node, SemanticsActions.OnLongClick, "onLongClick"
        )
        is UIAction.SetText -> {
            val setText = node.config.getOrNull(SemanticsActions.SetText)
                ?: return missingAction(request, "SetText")
            val lambda = setText.action
                ?: return missingActionLambda(request, "SetText")
            val handled = lambda.invoke(AnnotatedString(action.text))
            toActionResult(request, handled, "SetText")
        }
        is UIAction.ScrollBy -> {
            val scrollBy = node.config.getOrNull(SemanticsActions.ScrollBy)
                ?: return missingAction(request, "ScrollBy")
            val lambda = scrollBy.action
                ?: return missingActionLambda(request, "ScrollBy")
            val handled = lambda.invoke(action.deltaX, action.deltaY)
            toActionResult(request, handled, "ScrollBy")
        }
        is UIAction.ScrollToIndex -> {
            val scrollToIndex = node.config.getOrNull(SemanticsActions.ScrollToIndex)
                ?: return missingAction(request, "ScrollToIndex")
            val lambda = scrollToIndex.action
                ?: return missingActionLambda(request, "ScrollToIndex")
            val handled = lambda.invoke(action.index)
            toActionResult(request, handled, "ScrollToIndex")
        }
    }
}

private fun invokeNoArgAction(
    request: UIActionRequest,
    node: SemanticsNode,
    key: androidx.compose.ui.semantics.SemanticsPropertyKey<AccessibilityAction<() -> Boolean>>,
    name: String,
): UIActionResult {
    val action = node.config.getOrNull(key) ?: return missingAction(request, name)
    val lambda = action.action ?: return missingActionLambda(request, name)
    val handled = lambda.invoke()
    return toActionResult(request, handled, name)
}

private fun missingAction(request: UIActionRequest, name: String) = UIActionResult(
    uiActionRequestId = request.messageId,
    isSuccess = false,
    errorMessage = "Node ${request.nodeId} does not support $name",
)

private fun missingActionLambda(request: UIActionRequest, name: String) = UIActionResult(
    uiActionRequestId = request.messageId,
    isSuccess = false,
    errorMessage = "Node ${request.nodeId} has $name action without a handler",
)

private fun toActionResult(request: UIActionRequest, handled: Boolean, name: String) =
    if (handled) UIActionResult(uiActionRequestId = request.messageId, isSuccess = true)
    else UIActionResult(
        uiActionRequestId = request.messageId,
        isSuccess = false,
        errorMessage = "$name action returned false on node ${request.nodeId}",
    )

private fun findSemanticsNodeById(roots: List<SemanticsNode>, id: Int): SemanticsNode? {
    for (root in roots) findSemanticsNodeById(root, id)?.let { return it }
    return null
}

private fun findSemanticsNodeById(root: SemanticsNode, id: Int): SemanticsNode? {
    if (root.id == id) return root
    for (child in root.children) {
        findSemanticsNodeById(child, id)?.let { return it }
    }
    return null
}
