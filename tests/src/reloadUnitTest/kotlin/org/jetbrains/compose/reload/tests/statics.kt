package org.jetbrains.compose.reload.tests

import org.jetbrains.compose.reload.test.HotReloadUnitTest
import org.jetbrains.compose.reload.test.compileAndReload
import kotlin.test.assertEquals

@HotReloadUnitTest
fun `test - change Object property`() {
    assertEquals("foo", StaticsObject.property)
    compileAndReload(
        """
        package org.jetbrains.compose.reload.tests
        object StaticsObject {
            val property = "bar"
        }
    """.trimIndent()
    )
    assertEquals("bar", StaticsObject.property)
}

@HotReloadUnitTest
fun `test - change top level property`() {
    assertEquals("foo", topLevelProperty)
    compileAndReload(
        """
        @file:JvmName("staticsTopLevelProperty")
        package org.jetbrains.compose.reload.tests
        val topLevelProperty = "bar"
    """.trimIndent()
    )
    assertEquals("bar", topLevelProperty)
}
