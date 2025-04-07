/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package tests

import androidx.compose.material.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import org.jetbrains.compose.reload.test.HotReloadUnitTest
import org.jetbrains.compose.reload.test.compileAndReload

@OptIn(ExperimentalTestApi::class)
@HotReloadUnitTest
fun `test - #127 - remove override function`() = runComposeUiTest {
    setContent {
        Text(I127Class().foo(), modifier = Modifier.testTag("text"))
    }

    onNodeWithTag("text").assertTextEquals("bar")

    /* Remove the override */
    compileAndReload(
        """
        package tests
        open class I127Class : I127BaseClass()
    """.trimIndent()
    )
    onNodeWithTag("text").assertTextEquals("foo")
}

open class I127BaseClass {
    open fun foo() = "foo"
}

class I127Class : I127BaseClass() {
    override fun foo(): String = "bar"
}
