@file:OptIn(ExperimentalTime::class)

import org.jetbrains.compose.devtools.api.ReloadState
import org.jetbrains.compose.reload.core.getOrThrow
import org.jetbrains.compose.reload.orchestration.OrchestrationMessageId
import org.jetbrains.compose.reload.orchestration.encoderOfOrThrow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

class ReloadStateTest {
    val encoder = encoderOfOrThrow(ReloadState)
    val instant = Instant.fromEpochMilliseconds(1234567890)

    @Test
    fun `encode - decode - ok`() {
        val source = ReloadState.Ok(instant)
        assertEquals(source, encoder.decode(encoder.encode(source)).getOrThrow())
    }

    @Test
    fun `encode - decode - failed`() {
        val source = ReloadState.Failed(instant, "reason")
        assertEquals(source, encoder.decode(encoder.encode(source)).getOrThrow())

        val source2 = ReloadState.Failed(instant, "other")
        assertEquals(source2, encoder.decode(encoder.encode(source2)).getOrThrow())
    }

    @Test
    fun `encode - decode - reloading`() {
        val source = ReloadState.Reloading(instant)
        assertEquals(source, encoder.decode(encoder.encode(source)).getOrThrow())

        val source2 = ReloadState.Reloading(instant, OrchestrationMessageId("abcd"))
        assertEquals(source2, encoder.decode(encoder.encode(source2)).getOrThrow())
    }
}
