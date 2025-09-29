/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package tests

import org.jetbrains.compose.reload.test.HotReloadUnitTest
import org.jetbrains.compose.reload.test.compileAndReload
import kotlin.test.assertEquals
import kotlin.test.assertFails

private interface InterfaceWithStaticInt {
    companion object {
        @JvmStatic
        val myInt: Int = 100
    }
}

@HotReloadUnitTest
fun `test - interface with static int`() {
    assertEquals(InterfaceWithStaticInt.myInt, 100)
    compileAndReload(
        """
        package tests
        private interface InterfaceWithStaticInt {
            companion object {
                @JvmStatic
                val myInt: Int = 112
            }
        }

    """.trimIndent()
    )

    assertEquals(InterfaceWithStaticInt.myInt, 112)
}

private interface InterfaceWithStaticIntField {
    companion object {
        @JvmField
        val myInt: Int = 100
    }
}

@HotReloadUnitTest
fun `test - interface with static int field`() {
    assertEquals(InterfaceWithStaticIntField.myInt, 100)

    compileAndReload(
        """
        package tests
        private interface InterfaceWithStaticIntField {
            companion object {
                @JvmField
                val myInt: Int = 112
            }
        }

    """.trimIndent()
    )

    assertFails("112 would the desired value; Since interface fields have to be final, re-init is not yet supported") {
        assertEquals(InterfaceWithStaticInt.myInt, 112)
    }
}
