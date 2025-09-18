import org.jetbrains.compose.devtools.api.ReloadCountState
import org.jetbrains.compose.reload.core.getOrThrow
import org.jetbrains.compose.reload.orchestration.encoderOfOrThrow
import kotlin.io.encoding.Base64.Default.decode
import kotlin.test.Test
import kotlin.test.assertEquals

/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

class ReloadCountStateTest {

    private val encoder = encoderOfOrThrow(ReloadCountState)

    @Test
    fun `test - encode decode`() {
        val source = ReloadCountState(12, 24)
        assertEquals(source, encoder.decode(encoder.encode(source)).getOrThrow())
    }
}
