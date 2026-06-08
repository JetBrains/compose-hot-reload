/*
 * Copyright 2024-2026 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.jvm.tests

import org.jetbrains.compose.reload.jvm.JsonBuilder
import org.jetbrains.compose.reload.jvm.joinSemanticForest
import kotlin.test.Test
import kotlin.test.assertEquals

class SemanticForestTest {
    @Test
    fun `test - no roots produces error object`() {
        assertEquals("""{"error":"No semantic owners available"}""", joinSemanticForest(emptyList()))
    }

    @Test
    fun `test - single root is emitted as-is`() {
        assertEquals("""{"id":1}""", joinSemanticForest(listOf("""{"id":1}""")))
    }

    @Test
    fun `test - multiple roots produce an array`() {
        assertEquals(
            """[{"id":1},{"id":2,"isDialog":true}]""",
            joinSemanticForest(listOf("""{"id":1}""", """{"id":2,"isDialog":true}"""))
        )
    }
}

class JsonBuilderTest {
    private fun build(block: JsonBuilder.() -> Unit): String =
        buildString {
            append('{')
            JsonBuilder(this).block()
            append('}')
        }

    @Test
    fun `test - single string field`() {
        assertEquals("""{"k":"v"}""", build { str("k", "v") })
    }

    @Test
    fun `test - multiple fields get comma separated`() {
        assertEquals(
            """{"a":"1","b":2,"c":"3"}""",
            build {
                str("a", "1")
                raw("b", "2")
                str("c", "3")
            }
        )
    }

    @Test
    fun `test - escapes special characters`() {
        assertEquals(
            """{"k":"a\"b\\c\nd\re\tf"}""",
            build { str("k", "a\"b\\c\nd\re\tf") }
        )
    }

    @Test
    fun `test - raw values are not escaped`() {
        assertEquals(
            """{"flag":true,"n":42,"obj":{"x":1}}""",
            build {
                raw("flag", "true")
                raw("n", "42")
                raw("obj", """{"x":1}""")
            }
        )
    }

    @Test
    fun `test - nested produces independent comma tracking`() {
        val result = buildString {
            append('{')
            val outer = JsonBuilder(this)
            outer.str("name", "root")
            outer.comma()
            append("\"child\":{")
            val inner = outer.nested()
            inner.str("a", "1")
            inner.str("b", "2")
            append('}')
            append('}')
        }
        assertEquals("""{"name":"root","child":{"a":"1","b":"2"}}""", result)
    }

    @Test
    fun `test - empty builder produces no commas`() {
        assertEquals("{}", build { })
    }
}
