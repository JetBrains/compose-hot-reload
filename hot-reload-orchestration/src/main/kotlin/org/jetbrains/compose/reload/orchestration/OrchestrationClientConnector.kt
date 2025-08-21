/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.orchestration

import org.jetbrains.compose.reload.core.Future
import org.jetbrains.compose.reload.core.complete
import org.jetbrains.compose.reload.core.completeExceptionally
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.info
import org.jetbrains.compose.reload.core.withThread
import java.net.ServerSocket
import java.net.Socket

private val logger = createLogger()

internal sealed class OrchestrationClientConnector {

    abstract suspend fun connect(): OrchestrationIO

    data class ConnectToServer(val port: Int) : OrchestrationClientConnector() {
        override suspend fun connect(): OrchestrationIO {
            val writer = OrchestrationIO.newWriterThread()

            return try {
                withThread(writer) {
                    val socket = Socket("127.0.0.1", port)
                    socket.setOrchestrationDefaults()
                    OrchestrationIO(socket, writer = writer)
                }
            } catch (t: Throwable) {
                writer.close()
                throw t
            }
        }
    }

    data class AwaitServerConnection(val server: ServerSocket) : OrchestrationClientConnector() {
        val orchestrationServerPort = Future<Int>()

        override suspend fun connect(): OrchestrationIO {
            val reader = OrchestrationIO.newReaderThread()
            return try {
                withThread(reader) {
                    val socket = server.accept()
                    socket.setOrchestrationDefaults()

                    val io = OrchestrationIO(socket, reader = reader)

                    checkMagicNumberOrThrow(io.readInt())
                    val serverProtocolVersion = OrchestrationVersion(io.readInt())
                    val serverPort = io.readInt()

                    orchestrationServerPort.complete(serverPort)
                    logger.info("Accepted connection from server, protocol version: $serverProtocolVersion, port: $serverPort")
                    io
                }
            } catch (t: Throwable) {
                reader.close()
                orchestrationServerPort.completeExceptionally(t)
                throw t
            }
        }
    }
}

internal val OrchestrationClientConnector.port: Future<Int>
    get() = when (this) {
        is OrchestrationClientConnector.ConnectToServer -> Future(port)
        is OrchestrationClientConnector.AwaitServerConnection -> orchestrationServerPort
    }
