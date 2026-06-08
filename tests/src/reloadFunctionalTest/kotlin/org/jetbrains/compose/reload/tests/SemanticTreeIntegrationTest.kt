/*
 * Copyright 2024-2026 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.tests

import org.jetbrains.compose.reload.test.gradle.BuildGradleKtsExtension
import org.jetbrains.compose.reload.test.gradle.ExtendBuildGradleKts
import org.jetbrains.compose.reload.test.gradle.Headless
import org.jetbrains.compose.reload.test.gradle.HotReloadTest
import org.jetbrains.compose.reload.test.gradle.HotReloadTestFixture
import org.jetbrains.compose.reload.test.gradle.checkSemanticTree
import org.jetbrains.compose.reload.test.gradle.initialSourceCode
import org.jetbrains.compose.reload.utils.HostIntegrationTest
import org.jetbrains.compose.reload.utils.QuickTest
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.extension.ExtensionContext

class SemanticTreeIntegrationTest {

    // Pin text rendering to a bundled font so node widths/heights stay stable
    // across platforms (system default fonts differ between Linux and macOS).
    private val testFontPath: String = (Thread.currentThread().contextClassLoader
        .getResource("ResourcesTests/testFontResource.ttf")
        ?: error("Test font resource not found"))
        .toURI().let { java.nio.file.Paths.get(it).toString().replace('\\', '/') }

    private val semanticTreeColumnImports = """
    import androidx.compose.foundation.layout.Column
    import androidx.compose.foundation.text.BasicTextField
    import androidx.compose.material.Button
    import androidx.compose.material.Checkbox
    import androidx.compose.material.LinearProgressIndicator
    import androidx.compose.material.LocalTextStyle
    import androidx.compose.material.MaterialTheme
    import androidx.compose.material.Text
    import androidx.compose.material.Typography
    import androidx.compose.runtime.getValue
    import androidx.compose.runtime.mutableStateOf
    import androidx.compose.runtime.remember
    import androidx.compose.runtime.setValue
    import androidx.compose.ui.Modifier
    import androidx.compose.ui.semantics.*
    import androidx.compose.ui.text.font.FontFamily
    import androidx.compose.ui.text.platform.Font
    import java.io.File
""".trimIndent()

    private val semanticTreeColumnContent = """
    MaterialTheme(
        typography = Typography(
            defaultFontFamily = FontFamily(Font(File("$testFontPath")))
        )
    ) {
        Column {
            // text
            Text("Hello World!")

            // role=Button, onClick, children
            Button(onClick = {}) { Text("Click Me") }

            // enabled=false
            Button(onClick = {}, enabled = false) { Text("Disabled") }

            // toggleableState
            var checked by remember { mutableStateOf(true) }
            Checkbox(checked = checked, onCheckedChange = { checked = it })

            // progressBar
            var progress by remember { mutableStateOf(0.5f) }
            LinearProgressIndicator(progress = progress)

            // editableText
            var editable by remember { mutableStateOf("editable text") }
            BasicTextField(
                value = editable,
                onValueChange = { editable = it },
                textStyle = LocalTextStyle.current,
            )

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
    }
""".trimIndent()

    @Headless(false)
    @HostIntegrationTest
    @HotReloadTest
    @QuickTest
    @ExtendBuildGradleKts(PinUiScaleExtension::class)
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

    @Headless(false)
    @HostIntegrationTest
    @HotReloadTest
    @QuickTest
    @ExtendBuildGradleKts(PinUiScaleExtension::class)
    fun `test - get semantic tree with overlay`(fixture: HotReloadTestFixture) = fixture.runTest {
        assumeTrue(isInteractiveDesktopAvailable(), "Test requires an interactive desktop")
        fixture.launchAckSender()

        val windowsState = fixture.orchestration.states.get(org.jetbrains.compose.devtools.api.WindowsState)

        // A Popup renders in its own owner (a separate semantics root) within the same window,
        // exactly like a Dialog / ModalBottomSheet. Regression coverage for CMP-10282: the overlay
        // root must appear in the captured tree, turning the result into a forest of roots.
        //
        // The overlay owner's root fills the window, so its bounds reflect the window surface size,
        // i.e. the screen's backing scale. Pinning 'sun.java2d.uiScale=1' (see PinUiScaleExtension)
        // fixes that scale to 1 — which also makes the derived density 1, normalizing content-node
        // bounds too — so the golden tree is deterministic across machines.
        fixture initialSourceCode """
            import androidx.compose.foundation.layout.Column
            import androidx.compose.material.Button
            import androidx.compose.material.MaterialTheme
            import androidx.compose.material.Text
            import androidx.compose.material.Typography
            import androidx.compose.ui.text.font.FontFamily
            import androidx.compose.ui.text.platform.Font
            import androidx.compose.ui.unit.dp
            import androidx.compose.ui.window.*
            import java.io.File

            fun main() {
                singleWindowApplication(
                    state = WindowState(width = 400.dp, height = 600.dp),
                    undecorated = true,
                ) {
                    MaterialTheme(
                        typography = Typography(
                            defaultFontFamily = FontFamily(Font(File("$testFontPath")))
                        )
                    ) {
                        Column {
                            Button(onClick = {}) { Text("Main button") }
                        }
                        Popup {
                            Button(onClick = {}) { Text("In popup") }
                        }
                    }
                }
            }
            """.trimIndent()

        awaitOneWindow(windowsState)
        fixture.checkSemanticTree("semantic-tree")
    }

    @HotReloadTest
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

/**
 * Pins the application's UI scale to 1.
 * This keeps the golden semantic tree independent of the host's display scale.
 */
internal class PinUiScaleExtension : BuildGradleKtsExtension {
    override fun javaExecConfigure(context: ExtensionContext): String =
        """systemProperty("sun.java2d.uiScale", "1")"""
}
