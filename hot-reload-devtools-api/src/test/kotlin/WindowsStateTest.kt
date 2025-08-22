import org.jetbrains.compose.devtools.api.WindowsState
import org.jetbrains.compose.reload.core.WindowId
import org.jetbrains.compose.reload.core.getOrThrow
import org.jetbrains.compose.reload.orchestration.encoderOfOrThrow
import kotlin.test.Test
import kotlin.test.assertEquals

/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

class WindowsStateTest {
    private val encoder = encoderOfOrThrow<WindowsState>()

    @Test
    fun `test - encode decode`() {
        val empty = WindowsState(emptyMap())
        assertEquals(empty, encoder.decode(encoder.encode(empty)).getOrThrow())

        val nonEmpty = WindowsState(
            mapOf(
                WindowId.create() to WindowsState.WindowState(
                    x = 1, y = 2, width = 3, height = 4, isAlwaysOnTop = true
                )
            )
        )

        assertEquals(nonEmpty, encoder.decode(encoder.encode(nonEmpty)).getOrThrow())
    }
}
