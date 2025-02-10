package sample.library

import org.jetbrains.compose.reload.test.HotReloadUnitTest
import org.jetbrains.compose.reload.test.compileAndReload
import kotlin.test.assertEquals

@HotReloadUnitTest
fun `test - counter is incremented after hot reload`() {
    assertEquals(0, counter)
    compileAndReload("class Foo")
    assertEquals(1, counter)
}

@HotReloadUnitTest
fun `test - change function body`() {
    assertEquals("Before", ExampleApi.value())

    compileAndReload(
        """
            package sample.library
            
            object ExampleApi {
                fun value() = "After"
            }
        """.trimIndent()
    )

    assertEquals("After", ExampleApi.value())
}
