/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.orchestration

import org.intellij.lang.annotations.Language
import org.jetbrains.compose.reload.core.Logger
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.displayString
import org.jetbrains.compose.reload.core.encodeByteArray
import org.jetbrains.compose.reload.core.writeFrame
import org.jetbrains.compose.reload.core.writeString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.fail

class DecodeOrchestrationMessageTest {
    private val logs = mutableListOf<Logger.Log>()

    private val logger = createLogger(
        "test", level = Logger.Level.Debug,
        dispatch = listOf(Logger.Dispatch { logs.add(it) })
    )

    @Test
    fun `test - wrong package type`() {
        val frame = OrchestrationFrame(type = OrchestrationPackageType.JavaSerializableMessage, byteArrayOf())
        val decoded = frame.decodeOrchestrationMessage(logger)
        assertSame(frame, assertIs<OpaqueOrchestrationMessage>(decoded).frame)
        assertGetLog("""Expected Message, got JavaSerializableMessage""")
    }

    @Test
    fun `test - unknown encoding schema`() {
        val frame = OrchestrationFrame(OrchestrationPackageType.Message, encodeByteArray {
            writeShort(Short.MAX_VALUE.toInt())
        })

        val decoded = frame.decodeOrchestrationMessage(logger)
        assertSame(frame, assertIs<OpaqueOrchestrationMessage>(decoded).frame)
        assertGetLog("Unknown 'message encoding schema version'")
    }

    @Test
    fun `test - bad messageId frame`() {
        val frame = OrchestrationFrame(OrchestrationPackageType.Message, encodeByteArray {
            writeShort(1)
            writeInt(-1) // messageId length
        })

        val decoded = frame.decodeOrchestrationMessage(logger)
        assertSame(frame, assertIs<OpaqueOrchestrationMessage>(decoded).frame)
        val log = assertGetLog("Failed to decode encoded message.*")
        assertEquals("java.lang.IllegalArgumentException", log.throwableClassName)
        assertEquals("len < 0", log.throwableMessage)
    }

    @Test
    fun `test - no encoder`() {
        val frame = OrchestrationFrame(OrchestrationPackageType.Message, encodeByteArray {
            writeShort(1)
            writeFrame(OrchestrationMessageId.random().encodeToByteArray())
            writeString("foo") // classifier namespace
            writeString("bar") // classifier type
        })

        val decoded = frame.decodeOrchestrationMessage(logger)
        assertEquals(frame, assertIs<OpaqueOrchestrationMessage>(decoded).frame)
        assertGetLog("No encoder/decoder for classifier 'foo/bar'")
    }

    @Test
    fun `test - happy path`() {
        val encoder = TestEventEncoder()
        val testMessage = OrchestrationMessage.TestEvent("foo")
        val payload = encoder.encode(testMessage)
        val frame = OrchestrationFrame(OrchestrationPackageType.Message, encodeByteArray {
            writeShort(1)
            writeFrame(testMessage.messageId.encodeToByteArray())
            writeString(encoder.messageClassifier.namespace)
            writeString(encoder.messageClassifier.type)
            writeFrame(payload)
        })

        val decoded = frame.decodeOrchestrationMessage(logger)
        assertEquals(testMessage, assertIs<OrchestrationMessage.TestEvent>(decoded))
    }

    private fun assertGetLog(regex: Regex): Logger.Log {
        return logs.find { log -> regex.containsMatchIn(log.message) } ?: fail(
            """
            No log message matches regex: $regex
            ${logs.joinToString("\n") { it.displayString() }}
        """.trimIndent()
        )
    }

    private fun assertGetLog(@Language("RegExp") regex: String) = assertGetLog(Regex(regex))
}
