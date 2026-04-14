/*
 * Copyright 2024-2026 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.tests

import org.jetbrains.compose.reload.test.gradle.Headless
import org.jetbrains.compose.reload.test.gradle.HotReloadTest
import org.jetbrains.compose.reload.test.gradle.HotReloadTestFixture
import org.jetbrains.compose.reload.test.gradle.MinComposeVersion
import org.jetbrains.compose.reload.test.gradle.checkSemanticTree
import org.jetbrains.compose.reload.test.gradle.initialSourceCode
import org.jetbrains.compose.reload.utils.HostIntegrationTest
import org.jetbrains.compose.reload.utils.QuickTest
import org.junit.jupiter.api.Assumptions.assumeTrue

class SemanticTreeIntegrationTest {

    private val semanticTreeColumnImports = """
    import androidx.compose.foundation.layout.Column
    import androidx.compose.foundation.text.BasicTextField
    import androidx.compose.material.Button
    import androidx.compose.material.Checkbox
    import androidx.compose.material.LinearProgressIndicator
    import androidx.compose.material.Text
    import androidx.compose.ui.Modifier
    import androidx.compose.ui.semantics.*
""".trimIndent()

    private val semanticTreeColumnContent = """
    Column {
        // text
        Text("Hello World!")

        // role=Button, onClick, children
        Button(onClick = {}) { Text("Click Me") }

        // enabled=false
        Button(onClick = {}, enabled = false) { Text("Disabled") }

        // toggleableState
        Checkbox(checked = true, onCheckedChange = {})

        // progressBar
        LinearProgressIndicator(progress = 0.5f)

        // editableText
        BasicTextField(value = "editable text", onValueChange = {})

        // contentDescription + testTag + heading
        Text(
            "Annotated",
            Modifier.semantics {
                contentDescription = "custom description"
                testTag = "my-tag"
                heading()
            }
        )

        // stateDescription + selected
        Text(
            "Stateful",
            Modifier.semantics {
                stateDescription = "custom state"
                selected = true
            }
        )

        // onLongClick
        Button(
            onClick = {},
            modifier = Modifier.semantics { onLongClick { true } }
        ) { Text("Long press") }
    }
""".trimIndent()

    @Headless(false)
    @HostIntegrationTest
    @HotReloadTest
    @QuickTest
    fun `test - get semantic tree`(fixture: HotReloadTestFixture) = fixture.runTest {
        assumeTrue(isInteractiveDesktopAvailable(), "Test requires an interactive desktop")
        fixture.launchAckSender()

        val windowsState = fixture.orchestration.states.get(org.jetbrains.compose.devtools.api.WindowsState)

        fixture initialSourceCode """
            $semanticTreeColumnImports
            import androidx.compose.ui.unit.dp
            import androidx.compose.ui.window.*

            fun main() {
                singleWindowApplication(
                    state = WindowState(width = 400.dp, height = 600.dp),
                    undecorated = true,
                ) {
                    $semanticTreeColumnContent
                }
            }
            """.trimIndent()

        awaitOneWindow(windowsState)
        fixture.checkSemanticTree("semantic-tree")
    }

    @HotReloadTest
    @MinComposeVersion("1.9.0") // 1.8.2 generates slightly different semantic tree
    fun `test - get semantic tree headless`(fixture: HotReloadTestFixture) = fixture.runTest {
        fixture initialSourceCode """
            $semanticTreeColumnImports
            import org.jetbrains.compose.reload.test.screenshotTestApplication

            fun main() {
                screenshotTestApplication(width = 400, height = 600) {
                    $semanticTreeColumnContent
                }
            }
            """.trimIndent()

        fixture.checkSemanticTree("semantic-tree")
    }
}
