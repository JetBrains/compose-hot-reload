/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package tests

import org.jetbrains.compose.reload.test.HotReloadUnitTest
import org.jetbrains.compose.reload.test.compileAndReload
import kotlin.test.assertEquals

@HotReloadUnitTest
fun `test - change Object property`() {
    assertEquals("foo", StaticsObject.property)
    compileAndReload(
        """
        package tests
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
        package tests
        val topLevelProperty = "bar"
    """.trimIndent()
    )
    assertEquals("bar", topLevelProperty)
}
