/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.orchestration

import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import kotlin.test.assertEquals

class SerializationTest {
    @Test
    fun `test - reload request`() {
        val request = OrchestrationMessage.ReloadClassesRequest(
            mapOf(File("my/file") to OrchestrationMessage.ReloadClassesRequest.ChangeType.Modified)
        )

        assertEquals(
            expected = request, actual = request.encodeToFrame().decodePackage()
        )
    }

    @Test
    fun `test - introduction`() {
        val introduction = OrchestrationPackage.Introduction(
            clientId = OrchestrationClientId.random(),
            clientRole = OrchestrationClientRole.Unknown,
            clientPid = 1602
        )

        val frame = introduction.encodeToFrame()
        val deserialized = frame.decodePackage() as OrchestrationPackage.Introduction
        assertEquals(
            expected = introduction, actual = deserialized
        )
    }

    @Test
    fun `test - ack`() {
        val ack = OrchestrationPackage.Ack(
            messageId = OrchestrationMessageId.random()
        )

        assertEquals(ack, ack.encodeToFrame().decodePackage())
    }
}
