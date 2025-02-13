/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package tests

import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import org.jetbrains.compose.reload.DevelopmentEntryPoint
import org.jetbrains.compose.reload.test.HotReloadUnitTest
import org.jetbrains.compose.reload.test.compileAndReload

interface InvokeInterfaceTestInterface {
    fun invokeInterfaceMethod(): String
}

class InvokeInterfaceTestClass : InvokeInterfaceTestInterface {
    override fun invokeInterfaceMethod(): String = "foo"
}

@OptIn(ExperimentalTestApi::class)
@HotReloadUnitTest
fun `test - invokeInterface method dependency`() = runComposeUiTest {
    val instance: InvokeInterfaceTestInterface = InvokeInterfaceTestClass()

    setContent {
        DevelopmentEntryPoint {
            val text = remember { instance.invokeInterfaceMethod() }
            Text(text = text, modifier = Modifier.testTag("text"))
        }
    }

    onNodeWithTag("text").assertTextEquals("foo")

    compileAndReload(
        """
        package tests
        
        class InvokeInterfaceTestClass : InvokeInterfaceTestInterface {
            override fun invokeInterfaceMethod(): String = "bar"
        }
    """.trimIndent()
    )

    onNodeWithTag("text").assertTextEquals("bar")
}
