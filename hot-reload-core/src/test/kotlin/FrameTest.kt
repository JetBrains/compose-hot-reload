/*
 * Copyright 2024-2026 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

import org.jetbrains.compose.reload.core.readFrame
import org.jetbrains.compose.reload.core.writeFrame
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class FrameTest {
    @Test
    fun `test - frame round trip`() {
        val payload = byteArrayOf(1, 2, 3, 4, 5)
        val bytes = ByteArrayOutputStream().also { DataOutputStream(it).writeFrame(payload) }.toByteArray()

        assertContentEquals(payload, DataInputStream(ByteArrayInputStream(bytes)).readFrame())
    }

    @Test
    fun `test - truncated frame fails with EOFException`() {
        val bytes = ByteArrayOutputStream().also {
            DataOutputStream(it).apply { writeInt(10); write(byteArrayOf(1, 2, 3)) }
        }.toByteArray()

        assertFailsWith<EOFException> { DataInputStream(ByteArrayInputStream(bytes)).readFrame() }
    }

    @Test
    fun `test - negative frame size is malformed frame`() {
        val bytes = ByteArrayOutputStream().also { DataOutputStream(it).writeInt(-1) }.toByteArray()

        assertFailsWith<IllegalArgumentException> {
            DataInputStream(ByteArrayInputStream(bytes)).readFrame()
        }
    }
}
