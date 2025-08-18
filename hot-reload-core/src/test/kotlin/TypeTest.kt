import org.jetbrains.compose.reload.core.Future
import org.jetbrains.compose.reload.core.type
import kotlin.test.Test
import kotlin.test.assertEquals

/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

private typealias MyList = List<String>

class TypeTest {

    @Test
    fun `test - list`() {
        assertEquals("kotlin.collections.List<kotlin.String>", type<List<String>>().toString())
    }

    @Test
    fun `test - nullable`() {
        assertEquals("kotlin.String?", type<String?>().toString())
    }

    @Test
    fun `test - type alias`() {
        assertEquals("kotlin.collections.List<kotlin.String>", type<MyList>().toString())
    }

    @Test
    fun `test  - variance and star projection`() {
        @Suppress("REDUNDANT_PROJECTION")
        assertEquals(
            "org.jetbrains.compose.reload.core.Future<out kotlin.collections.List<*>>",
            type<Future<out List<*>>>().toString()
        )
    }
}
