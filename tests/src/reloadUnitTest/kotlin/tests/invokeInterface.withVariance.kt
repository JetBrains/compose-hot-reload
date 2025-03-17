/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package tests

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

interface InvokeInterfaceWithVarianceInterface<T> {
    fun invokeInterfaceMethod(param: T): String
}

class InvokeInterfaceWithVarianceTestClass : InvokeInterfaceWithVarianceInterface<String> {
    override fun invokeInterfaceMethod(param: String): String {
        return "before: $param"
    }
}


object InvokeInterfaceWithVariance {
    val instance: InvokeInterfaceWithVarianceInterface<String> = InvokeInterfaceWithVarianceTestClass()

    /*
    We're using a dedicated function for this test to work around
    https://youtrack.jetbrains.com/issue/KT-75159/
    */
    @Composable
    fun render() {
        val text = remember { instance.invokeInterfaceMethod("foo") }
        Text(text = text, modifier = Modifier.testTag("text"))
    }
}

@OptIn(ExperimentalTestApi::class)
@HotReloadUnitTest
fun `test - invokeInterface method dependency - with variance`() = runComposeUiTest {
    setContent {
        DevelopmentEntryPoint {
            InvokeInterfaceWithVariance.render()
        }
    }

    onNodeWithTag("text").assertTextEquals("before: foo")

    compileAndReload(
        """
        package tests
        
        class InvokeInterfaceWithVarianceTestClass : InvokeInterfaceWithVarianceInterface<String> {
            override fun invokeInterfaceMethod(param: String): String {
                return "after: %param"
            }
        }
    """.trimIndent().replace("%", "$")
    )

    onNodeWithTag("text").assertTextEquals("after: foo")

}
