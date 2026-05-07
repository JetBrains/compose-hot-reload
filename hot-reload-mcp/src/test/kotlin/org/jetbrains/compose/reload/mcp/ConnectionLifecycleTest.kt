/*
 * Copyright 2024-2026 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.mcp

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.jetbrains.compose.reload.core.PidFileInfo
import org.jetbrains.compose.reload.core.awaitOrThrow
import org.jetbrains.compose.reload.core.writePidFile
import org.jetbrains.compose.reload.orchestration.startOrchestrationServer
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class ConnectionLifecycleTest {

    @Test
    fun `test - waitForOrchestrationPort finds port from PID file`() = runTest(timeout = 10.seconds) {
        val tempDir = createTempDirectory("mcp-test")
        try {
            val pidFile = tempDir.resolve("test.pid")
            pidFile.writePidFile(PidFileInfo(pid = 123, orchestrationPort = 5555))

            val port = waitForOrchestrationPort(pidFile)
            assertEquals(5555, port)
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `test - waitForOrchestrationPort waits until PID file appears`() = runBlocking {
        withTimeout(30.seconds) {
            val tempDir = createTempDirectory("mcp-test")
            try {
                val pidFile = tempDir.resolve("test.pid")

                val portDeferred = async(Dispatchers.IO) {
                    waitForOrchestrationPort(pidFile)
                }

                // Give the WatchService time to register before writing the PID file
                delay(500.milliseconds)
                pidFile.writePidFile(PidFileInfo(pid = 123, orchestrationPort = 7777))

                assertEquals(7777, portDeferred.await())
            } finally {
                tempDir.toFile().deleteRecursively()
            }
        }
    }

    @Test
    fun `test - connectionLoop connects and detects disconnect`() = runTest(timeout = 30.seconds) {
        val tempDir = createTempDirectory("mcp-test")
        val server = startOrchestrationServer()
        val flowScope = CoroutineScope(Dispatchers.IO)
        try {
            val port = server.port.awaitOrThrow()
            val pidFile = tempDir.resolve("test.pid")
            pidFile.writePidFile(PidFileInfo(pid = ProcessHandle.current().pid(), orchestrationPort = port))

            val orchestration = connectionLoop(pidFile)
                .stateIn(flowScope, SharingStarted.Eagerly, null)

            // Wait for connection
            orchestration.first { it != null }

            // Close the server → triggers disconnect
            server.close()

            // Wait for disconnection
            orchestration.first { it == null }
        } finally {
            flowScope.cancel()
            server.close()
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `test - connectionLoop reconnects after app restart`() = runTest(timeout = 60.seconds) {
        val tempDir = createTempDirectory("mcp-test")
        val server1 = startOrchestrationServer()
        val flowScope = CoroutineScope(Dispatchers.IO)
        try {
            val port1 = server1.port.awaitOrThrow()
            val pidFile = tempDir.resolve("test.pid")
            pidFile.writePidFile(PidFileInfo(pid = ProcessHandle.current().pid(), orchestrationPort = port1))

            val orchestration = connectionLoop(pidFile)
                .stateIn(flowScope, SharingStarted.Eagerly, null)

            // Wait for first connection
            orchestration.first { it != null }

            // Simulate app shutdown
            server1.close()
            orchestration.first { it == null }

            // Simulate app restart with a new server
            val server2 = startOrchestrationServer()
            try {
                val port2 = server2.port.awaitOrThrow()
                pidFile.writePidFile(PidFileInfo(pid = ProcessHandle.current().pid(), orchestrationPort = port2))

                // Wait for reconnection
                orchestration.first { it != null }
            } finally {
                server2.close()
            }
        } finally {
            flowScope.cancel()
            server1.close()
            tempDir.toFile().deleteRecursively()
        }
    }
}
