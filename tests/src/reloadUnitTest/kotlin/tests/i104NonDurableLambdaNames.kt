/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package tests

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import org.jetbrains.compose.reload.test.HotReloadUnitTest
import org.jetbrains.compose.reload.test.compileAndReload
import utils.readSource

@OptIn(ExperimentalTestApi::class)
@HotReloadUnitTest
fun `test - #104 -remembered composable lambdas produce non-durable class names`() = runComposeUiTest {
    setContent {
        I104NonDurableLambdaNames.render()
    }

    var source = readSource("i104NonDurableLambdaNames.objects.kt")
    source = source
        .replace("import androidx.compose.material3.Surface", "import androidx.compose.material3.Card")
        .replace("Surface {", "Card {")

    compileAndReload(source)
}

@OptIn(ExperimentalTestApi::class)
@HotReloadUnitTest
fun `test - #104 - inline function overloads produce non-durable default lambda names`() = runComposeUiTest {
    setContent {
        I104DefaultLambdaNames.render()
    }

    var source = readSource("i104NonDurableLambdaNames.objects.kt")
    source = source
        .replace(
            "inline fun <T> inlineHazard(a: List<T>, noinline b: (item: T) -> Any? = { null }, c: (item: T) -> Unit) = Unit",
            "inline fun inlineHazard(a: IntArray, noinline b: (item: Int, Any) -> Any? = { a, b -> null }, c: (item: Int) -> Unit) = Unit"
        )
        .replace("val array = mutableListOf(\"A\", \"B\", \"C\")", "val array = intArrayOf(1, 2, 3)")

    compileAndReload(source)
}
