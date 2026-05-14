/*
 * Copyright 2024-2026 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.tests

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.SemanticTreeRequest
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.SemanticTreeResult
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.UIAction
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.UIActionRequest
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.UIActionResult
import org.jetbrains.compose.reload.test.gradle.HotReloadTest
import org.jetbrains.compose.reload.test.gradle.HotReloadTestFixture
import org.jetbrains.compose.reload.test.gradle.MinComposeVersion
import org.jetbrains.compose.reload.test.gradle.initialSourceCode
import org.jetbrains.compose.reload.utils.QuickTest
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@MinComposeVersion("1.9.0")
@QuickTest
class UIActionIntegrationTest {

    @HotReloadTest
    fun `test - click action`(fixture: HotReloadTestFixture) = fixture.runTest {
        fixture initialSourceCode """
            import androidx.compose.material.Button
            import androidx.compose.material.Text
            import androidx.compose.runtime.*
            import org.jetbrains.compose.reload.test.screenshotTestApplication

            fun main() = screenshotTestApplication(width = 300, height = 200) {
                var count by remember { mutableStateOf(0) }
                Button(onClick = { count++ }) { Text("Count: ${'$'}count") }
            }
        """.trimIndent()

        val initialTree = fixture.fetchSemanticTree()
        val buttonId = findNodeIdByText(initialTree, "Count: 0")
            ?: error("Could not locate button in initial tree:\n$initialTree")

        val result = fixture.dispatchUIAction(UIActionRequest(nodeId = buttonId, action = UIAction.Click))
        assertTrue(result.isSuccess, "Click failed: ${result.errorMessage}")

        val updatedTree = fixture.fetchSemanticTree()
        assertNotNull(
            findNodeIdByText(updatedTree, "Count: 1"),
            "Expected counter to increment after click. Tree:\n$updatedTree"
        )
    }

    @HotReloadTest
    fun `test - long_click action`(fixture: HotReloadTestFixture) = fixture.runTest {
        fixture initialSourceCode """
            import androidx.compose.foundation.combinedClickable
            import androidx.compose.foundation.interaction.MutableInteractionSource
            import androidx.compose.foundation.layout.Box
            import androidx.compose.material.Text
            import androidx.compose.runtime.*
            import androidx.compose.ui.Modifier
            import org.jetbrains.compose.reload.test.screenshotTestApplication

            @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
            fun main() = screenshotTestApplication(width = 300, height = 200) {
                var label by remember { mutableStateOf("initial") }
                val interactionSource = remember { MutableInteractionSource() }
                Box(
                    Modifier.combinedClickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = {},
                        onLongClick = { label = "long-clicked" },
                    )
                ) { Text(label) }
            }
        """.trimIndent()

        val initialTree = fixture.fetchSemanticTree()
        val targetId = findNodeIdWithAction(initialTree, "onLongClick")
            ?: error("Could not find a node with onLongClick action in tree:\n$initialTree")

        val result = fixture.dispatchUIAction(UIActionRequest(nodeId = targetId, action = UIAction.LongClick))
        assertTrue(result.isSuccess, "LongClick failed: ${result.errorMessage}")

        val updatedTree = fixture.fetchSemanticTree()
        assertNotNull(
            findNodeIdByText(updatedTree, "long-clicked"),
            "Expected label to change after long click. Tree:\n$updatedTree"
        )
    }

    @HotReloadTest
    fun `test - type_text action`(fixture: HotReloadTestFixture) = fixture.runTest {
        fixture initialSourceCode """
            import androidx.compose.foundation.text.BasicTextField
            import androidx.compose.material.LocalTextStyle
            import androidx.compose.runtime.*
            import org.jetbrains.compose.reload.test.screenshotTestApplication

            fun main() = screenshotTestApplication(width = 300, height = 200) {
                var value by remember { mutableStateOf("initial") }
                BasicTextField(
                    value = value,
                    onValueChange = { value = it },
                    textStyle = LocalTextStyle.current,
                )
            }
        """.trimIndent()

        val initialTree = fixture.fetchSemanticTree()
        val fieldId = findEditableNodeId(initialTree)
            ?: error("Could not locate editable text field in tree:\n$initialTree")

        val result = fixture.dispatchUIAction(
            UIActionRequest(nodeId = fieldId, action = UIAction.SetText("updated"))
        )
        assertTrue(result.isSuccess, "SetText failed: ${result.errorMessage}")

        val updatedTree = fixture.fetchSemanticTree()
        val updatedField = findNodeByPredicate(updatedTree) { node ->
            (node["editableText"] as? JsonPrimitive)?.contentOrNull == "updated"
        }
        assertNotNull(updatedField, "Expected editable text to be 'updated'. Tree:\n$updatedTree")
    }

    @HotReloadTest
    fun `test - scroll action`(fixture: HotReloadTestFixture) = fixture.runTest {
        fixture initialSourceCode """
            import androidx.compose.foundation.lazy.LazyColumn
            import androidx.compose.foundation.lazy.items
            import androidx.compose.material.Text
            import androidx.compose.ui.Modifier
            import androidx.compose.ui.platform.testTag
            import org.jetbrains.compose.reload.test.screenshotTestApplication

            fun main() = screenshotTestApplication(width = 300, height = 200) {
                LazyColumn(modifier = Modifier.testTag("scroller")) {
                    items((1..50).toList()) { Text("Item ${'$'}it") }
                }
            }
        """.trimIndent()

        val initialTree = fixture.fetchSemanticTree()
        val scrollableId = findNodeIdByTestTag(initialTree, "scroller")
            ?: error("Could not find scrollable container in tree:\n$initialTree")
        assertNotNull(
            findNodeIdByText(initialTree, "Item 1"),
            "Expected 'Item 1' to be visible before scroll. Tree:\n$initialTree"
        )
        assertNull(
            findNodeIdByText(initialTree, "Item 45"),
            "Expected 'Item 45' to NOT be visible before scroll. Tree:\n$initialTree"
        )

        val result = fixture.dispatchUIAction(
            UIActionRequest(nodeId = scrollableId, action = UIAction.ScrollBy(deltaX = 0f, deltaY = 1000f))
        )
        assertTrue(result.isSuccess, "ScrollBy failed: ${result.errorMessage}")

        val updatedTree = fixture.fetchSemanticTree()
        assertNull(
            findNodeIdByText(updatedTree, "Item 1"),
            "Expected 'Item 1' to be scrolled out of view. Tree:\n$updatedTree"
        )
        assertNotNull(
            findNodeIdByText(updatedTree, "Item 45"),
            "Expected a later item (e.g. 'Item 45') to be visible after scroll. Tree:\n$updatedTree"
        )
    }

    @HotReloadTest
    fun `test - scroll_to_index action`(fixture: HotReloadTestFixture) = fixture.runTest {
        fixture initialSourceCode """
            import androidx.compose.foundation.lazy.LazyColumn
            import androidx.compose.foundation.lazy.items
            import androidx.compose.material.Text
            import androidx.compose.ui.Modifier
            import androidx.compose.ui.platform.testTag
            import org.jetbrains.compose.reload.test.screenshotTestApplication

            fun main() = screenshotTestApplication(width = 300, height = 200) {
                LazyColumn(modifier = Modifier.testTag("scroller")) {
                    items((1..50).toList()) { Text("Item ${'$'}it") }
                }
            }
        """.trimIndent()

        val initialTree = fixture.fetchSemanticTree()
        val scrollableId = findNodeIdByTestTag(initialTree, "scroller")
            ?: error("Could not find scrollable container in tree:\n$initialTree")
        assertNotNull(
            findNodeIdByText(initialTree, "Item 1"),
            "Expected 'Item 1' to be visible before scroll. Tree:\n$initialTree"
        )
        assertNull(
            findNodeIdByText(initialTree, "Item 45"),
            "Expected 'Item 45' to NOT be visible before scroll. Tree:\n$initialTree"
        )

        val result = fixture.dispatchUIAction(
            UIActionRequest(nodeId = scrollableId, action = UIAction.ScrollToIndex(44))
        )
        assertTrue(result.isSuccess, "ScrollToIndex failed: ${result.errorMessage}")

        val updatedTree = fixture.fetchSemanticTree()
        assertNotNull(
            findNodeIdByText(updatedTree, "Item 45"),
            "Expected 'Item 45' to be visible after scrollToIndex(44). Tree:\n$updatedTree"
        )
        assertNull(
            findNodeIdByText(updatedTree, "Item 1"),
            "Expected 'Item 1' to be out of view after scrollToIndex(44). Tree:\n$updatedTree"
        )
    }

    @HotReloadTest
    fun `test - action on non-existent node returns error`(fixture: HotReloadTestFixture) = fixture.runTest {
        fixture initialSourceCode """
            import androidx.compose.material.Text
            import org.jetbrains.compose.reload.test.screenshotTestApplication

            fun main() = screenshotTestApplication(width = 300, height = 200) {
                Text("Hello")
            }
        """.trimIndent()

        // Wait for the app to be ready (semantic tree available)
        fixture.fetchSemanticTree()

        val result = fixture.dispatchUIAction(
            UIActionRequest(nodeId = 99_999, action = UIAction.Click)
        )
        assertFalse(result.isSuccess, "Action on non-existent node should fail")
        assertNotNull(result.errorMessage, "Error message expected")
        assertTrue(
            result.errorMessage!!.contains("not found", ignoreCase = true),
            "Error message should mention 'not found' but was: ${result.errorMessage}"
        )
    }
}

// ── helpers ──────────────────────────────────────────────────────────────────

private suspend fun HotReloadTestFixture.fetchSemanticTree(): String {
    val request = SemanticTreeRequest()
    return sendMessage(request) {
        skipToMessage<SemanticTreeResult> { it.semanticTreeRequestId == request.messageId }
    }.tree
}

private suspend fun HotReloadTestFixture.dispatchUIAction(request: UIActionRequest): UIActionResult =
    sendMessage(request) {
        skipToMessage<UIActionResult> { it.uiActionRequestId == request.messageId }
    }

private val jsonParser = Json { ignoreUnknownKeys = true; isLenient = true }

private fun parseTree(tree: String): JsonObject = jsonParser.parseToJsonElement(tree).jsonObject

private fun findNodeByPredicate(tree: String, predicate: (JsonObject) -> Boolean): JsonObject? =
    findNodeByPredicate(parseTree(tree), predicate)

private fun findNodeByPredicate(node: JsonObject, predicate: (JsonObject) -> Boolean): JsonObject? {
    if (predicate(node)) return node
    val children = node["children"] ?: return null
    for (child in children.jsonArray) {
        findNodeByPredicate(child.jsonObject, predicate)?.let { return it }
    }
    return null
}

private fun findNodeIdByText(tree: String, text: String): Int? =
    findNodeByPredicate(tree) { node ->
        (node["text"] as? JsonPrimitive)?.contentOrNull == text
    }?.get("id")?.jsonPrimitive?.intOrNull

private fun findNodeIdByTestTag(tree: String, tag: String): Int? =
    findNodeByPredicate(tree) { node ->
        (node["testTag"] as? JsonPrimitive)?.contentOrNull == tag
    }?.get("id")?.jsonPrimitive?.intOrNull

private fun findNodeIdWithAction(tree: String, action: String): Int? =
    findNodeByPredicate(tree) { node ->
        val actions = node["actions"]?.jsonArray ?: return@findNodeByPredicate false
        actions.any { (it as? JsonPrimitive)?.contentOrNull == action }
    }?.get("id")?.jsonPrimitive?.intOrNull

private fun findEditableNodeId(tree: String): Int? =
    findNodeByPredicate(tree) { node -> node.containsKey("editableText") }
        ?.get("id")?.jsonPrimitive?.intOrNull
