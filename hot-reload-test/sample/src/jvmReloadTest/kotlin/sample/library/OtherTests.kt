package sample.library

import org.jetbrains.compose.reload.test.HotReloadUnitTest
import org.jetbrains.compose.reload.test.compileAndReload
import kotlin.test.assertEquals

@HotReloadUnitTest
fun `test - noop`() {

}

@HotReloadUnitTest
fun `test - failure`() {
    assertEquals("Foo", "Bar")
}
