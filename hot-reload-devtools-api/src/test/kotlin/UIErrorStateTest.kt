/*
 * Copyright 2024-2026 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

import org.jetbrains.compose.devtools.api.UIErrorState
import org.jetbrains.compose.reload.core.WindowId
import org.jetbrains.compose.reload.core.getOrThrow
import org.jetbrains.compose.reload.orchestration.encoderOfOrThrow
import kotlin.test.Test
import kotlin.test.assertEquals

class UIErrorStateTest {
    private val encoder = encoderOfOrThrow(UIErrorState)

    @Test
    fun `test - encode decode`() {
        val empty = UIErrorState(emptyMap())
        assertEquals(empty, encoder.decode(encoder.encode(empty)).getOrThrow())

        val nonEmpty = UIErrorState(
            mapOf(
                WindowId.create() to UIErrorState.UIError(
                    message = "boom",
                    stacktrace = listOf("a.b.C.foo(C.kt:1)", "a.b.C.bar(C.kt:2)"),
                ),
                WindowId.create() to UIErrorState.UIError(
                    message = null,
                    stacktrace = emptyList(),
                ),
            )
        )

        assertEquals(nonEmpty, encoder.decode(encoder.encode(nonEmpty)).getOrThrow())
    }
}
