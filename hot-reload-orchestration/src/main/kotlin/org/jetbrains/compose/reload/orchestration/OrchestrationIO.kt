/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.orchestration

import org.jetbrains.compose.reload.core.WorkerThread
import org.jetbrains.compose.reload.core.getOrThrow
import org.jetbrains.compose.reload.core.withThread
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

internal interface OrchestrationIO {
    suspend infix fun writeInt(value: Int)
    suspend infix fun writeShort(value: Short)
    suspend infix fun writeByte(value: Byte)
    suspend infix fun writePackage(pkg: OrchestrationPackage)

    suspend fun readInt(): Int
    suspend fun readShort(): Short
    suspend fun readByte(): Byte

    /**
     * Returns `null` if the IO is closed
     */
    suspend fun readPackage(): OrchestrationPackage?

    fun isClosed(): Boolean
    suspend fun close()
}


internal suspend fun OrchestrationIO(
    socket: Socket, writer: WorkerThread, reader: WorkerThread
): OrchestrationIO {
    val outputStream = withThread(writer) { DataOutputStream(socket.outputStream.buffered()) }
    val inputStream = withThread(reader) { DataInputStream(socket.inputStream.buffered()) }
    return OrchestrationIOImpl(socket, reader, writer, outputStream, inputStream)
}

internal class OrchestrationIOImpl(
    private val socket: Socket,
    private val reader: WorkerThread,
    private val writer: WorkerThread,
    private val output: DataOutputStream,
    private val input: DataInputStream
) : OrchestrationIO {

    private val isClosed = AtomicBoolean(false)

    override suspend fun writeInt(value: Int) = withThread(writer) {
        output.writeInt(value)
        output.flush()
    }

    override suspend fun writeShort(value: Short) = withThread(writer) {
        output.writeShort(value.toInt())
        output.flush()
    }

    override suspend fun writeByte(value: Byte) = withThread(writer) {
        output.writeByte(value.toInt())
        output.flush()
    }

    override suspend fun writePackage(pkg: OrchestrationPackage) = withThread(writer) {
        val frame = pkg.encodeToFrame()
        output.writeInt(frame.type.intValue)
        output.writeInt(frame.data.size)
        output.write(frame.data)
        output.flush()
    }

    override suspend fun readInt(): Int = withThread(reader) {
        input.readInt()
    }

    override suspend fun readShort(): Short = withThread(reader) {
        input.readShort()
    }

    override suspend fun readByte(): Byte = withThread(reader) {
        input.readByte()
    }

    override suspend fun readPackage(): OrchestrationPackage? = withThread(reader) {
        try {
            val pkgType = OrchestrationPackageType.from(input.readInt()).getOrThrow()
            val pkgSize = input.readInt()
            if (pkgSize < 0) error("Illegal pkg size: '$pkgSize'")
            if (pkgSize > 12e6) error("Illegal pkg size: '$pkgSize'")
            val pkgData = input.readNBytes(pkgSize)
            val frame = OrchestrationFrame(pkgType, pkgData)
            frame.decodePackage()
        } catch (t: Throwable) {
            if (isClosed.get()) {
                return@withThread null
            }
            throw t
        }
    }

    override suspend fun close() {
        if (!isClosed.getAndSet(true)) return
        writer.shutdown().await()
        input.close()
        output.close()
        reader.shutdown().await()
        socket.close()
    }

    override fun isClosed(): Boolean {
        return isClosed.get()
    }
}
