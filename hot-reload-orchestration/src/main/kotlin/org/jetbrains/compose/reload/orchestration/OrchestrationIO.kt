/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.orchestration

import org.jetbrains.compose.reload.core.WorkerThread
import org.jetbrains.compose.reload.core.awaitOrThrow
import org.jetbrains.compose.reload.core.getOrNull
import org.jetbrains.compose.reload.core.getOrThrow
import org.jetbrains.compose.reload.core.invokeOnCompletion
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

    companion object {
        fun newWriterThread() = WorkerThread("Orchestration IO: Writer")
        fun newReaderThread() = WorkerThread("Orchestration IO: Reader")
    }
}

internal fun OrchestrationIO(
    socket: Socket,
    writer: WorkerThread = OrchestrationIO.newWriterThread(),
    reader: WorkerThread = OrchestrationIO.newReaderThread()
): OrchestrationIO {
    return OrchestrationIOImpl(socket, writer = writer, reader = reader)
}

internal class OrchestrationIOImpl(
    private val socket: Socket, private val writer: WorkerThread, private val reader: WorkerThread,
) : OrchestrationIO {

    private val isClosed = AtomicBoolean(false)
    private val output = writer.invoke { DataOutputStream(socket.outputStream.buffered()) }
    private val input = reader.invoke { DataInputStream(socket.inputStream.buffered()) }

    override suspend fun writeInt(value: Int) = withThread(writer) {
        val output = output.awaitOrThrow()
        output.writeInt(value)
        output.flush()
    }

    override suspend fun writeShort(value: Short) = withThread(writer) {
        val output = output.awaitOrThrow()
        output.writeShort(value.toInt())
        output.flush()
    }

    override suspend fun writeByte(value: Byte) = withThread(writer) {
        val output = output.awaitOrThrow()
        output.writeByte(value.toInt())
        output.flush()
    }

    override suspend fun writePackage(pkg: OrchestrationPackage) = withThread(writer) {
        val output = output.awaitOrThrow()
        val frame = pkg.encodeToFrame()
        output.writeInt(frame.type.intValue)
        output.writeInt(frame.data.size)
        output.write(frame.data)
        output.flush()
    }

    override suspend fun readInt(): Int = withThread(reader) {
        input.awaitOrThrow().readInt()
    }

    override suspend fun readShort(): Short = withThread(reader) {
        input.awaitOrThrow().readShort()
    }

    override suspend fun readByte(): Byte = withThread(reader) {
        input.awaitOrThrow().readByte()
    }

    override suspend fun readPackage(): OrchestrationPackage? = withThread(reader) {
        val input = input.awaitOrThrow()
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
        input.invokeOnCompletion { value -> value.getOrNull()?.close() }
        output.invokeOnCompletion { value -> value.getOrNull()?.close() }
        reader.shutdown().await()
        socket.close()
    }

    override fun isClosed(): Boolean {
        return isClosed.get()
    }
}
