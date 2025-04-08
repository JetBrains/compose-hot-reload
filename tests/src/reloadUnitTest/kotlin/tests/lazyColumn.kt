/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package tests

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.runComposeUiTest
import org.jetbrains.compose.reload.test.HotReloadUnitTest
import org.jetbrains.compose.reload.test.compileAndReload

internal object IntItemRender {
    fun render(value: Int) = "Before: $value"
}

internal object StringItemRender {
    fun render(value: String) = "Before: $value"
}

@OptIn(ExperimentalTestApi::class)
@HotReloadUnitTest
fun `test - LazyColumn`() = runComposeUiTest {
    val elements = listOf("A", "B", "C", 1, 2, 3)

    setContent {
        LazyColumn {
            items(elements) { element ->
                when (element) {
                    is String -> Text(StringItemRender.render(element), modifier = Modifier.testTag("item"))
                    is Int -> Text(IntItemRender.render(element), modifier = Modifier.testTag("item"))
                }
            }
        }
    }

    onAllNodes(hasTestTag("item")).assertCountEquals(elements.size).run {
        val expected = listOf("Before: A", "Before: B", "Before: C", "Before: 1", "Before: 2", "Before: 3")
        repeat(elements.size) { index ->
            get(index).assertTextEquals(expected[index])
        }
    }

    compileAndReload(
        """
        package tests
        internal object StringItemRender {
            fun render(value: String) = "After: %value"
        }
    """.trimIndent().replace("%", "$")
    )

    onAllNodes(hasTestTag("item")).assertCountEquals(elements.size).run {
        val expected = listOf("After: A", "After: B", "After: C", "Before: 1", "Before: 2", "Before: 3")
        repeat(elements.size) { index ->
            get(index).assertTextEquals(expected[index])
        }
    }
}
