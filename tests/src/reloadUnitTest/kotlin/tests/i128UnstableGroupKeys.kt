package tests

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.runComposeUiTest
import org.jetbrains.compose.reload.test.HotReloadUnitTest
import org.jetbrains.compose.reload.test.compileAndReload
import utils.readSource

@OptIn(ExperimentalTestApi::class)
@HotReloadUnitTest
fun `test - when`() = runComposeUiTest {
    setContent {
        WhenStatement.content()
    }

    onAllNodesWithTag("item").assertCountEquals(2).run {
        get(0).assertTextEquals("(Before): A")
        get(1).assertTextEquals("(Before): 1")
    }

    var source = readSource("i128UnstableGroupKeys.object.kt")
    source = source.replace(
        """.testTag("item")""", """
        .testTag("item")
        .padding(12.dp) // Add additional call to the modifier to change offsets!
    """.trimIndent()
    )

    compileAndReload(source)

    onAllNodesWithTag("item").assertCountEquals(2).run {
        get(0).assertTextEquals("(Before): A")
        get(1).assertTextEquals("(Before): 1")
    }

    source = source.replace("(Before)", "(After)")
    compileAndReload(source)

    onAllNodesWithTag("item").assertCountEquals(2).run {
        get(0).assertTextEquals("(After): A")
        get(1).assertTextEquals("(After): 1")
    }
}
