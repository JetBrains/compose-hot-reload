package org.jetbrains.compose.reload.tests

import org.jetbrains.compose.reload.test.HotReloadUnitTest
import org.jetbrains.compose.reload.test.compileAndReload
import kotlin.test.assertEquals

@HotReloadUnitTest
fun `test - add enum case`() {
    assertEquals(listOf("A", "B", "C"), EnumCases.entries.map { it.name })
    compileAndReload(
        """
        package org.jetbrains.compose.reload.tests

        enum class EnumCases {
            A, B, C, D
        }
    """.trimIndent()
    )

    assertEquals(listOf("A", "B", "C", "D"), EnumCases.entries.map { it.name })
}

@HotReloadUnitTest
fun `test - remove enum case`() {
    assertEquals(listOf("A", "B", "C"), EnumCases.entries.map { it.name })
    compileAndReload(
        """
        package org.jetbrains.compose.reload.tests

        enum class EnumCases {
            A, B,
        }
    """.trimIndent()
    )

    assertEquals(listOf("A", "B"), EnumCases.entries.map { it.name })
}
