/*
 * Copyright 2024-2026 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.mcp

import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.io.path.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * CMP-11137: a stdio MCP server must exit once the client closes its stdin.
 * Launches the real [main] in a fresh JVM, closes stdin, and asserts that the server JVM exits cleanly.
 */
class McpMainStdinEofTest {

    private val tempDir = createTempDirectory("mcp-stdin-eof")
    private var server: Process? = null

    @AfterTest
    fun cleanup() {
        server?.destroyForcibly()?.waitFor()
        tempDir.toFile().deleteRecursively()
    }

    @Test
    fun `test - server exits when the client closes stdin`() {
        val isWindows = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
        val java = Path(System.getProperty("java.home"), "bin", if (isWindows) "java.exe" else "java").toString()
        val stderrLog = tempDir.resolve("stderr.log")

        val process = ProcessBuilder(
            java, "-cp", System.getProperty("java.class.path"),
            "org.jetbrains.compose.reload.mcp.ComposeHotReloadMcp",
            // The PID file never appears: no application ever connects, which parks the
            // `McpMain.connectionLoop` in 'WatchService.take()'.
            tempDir.resolve("app.pid").toString(),
        )
            .redirectError(stderrLog.toFile())
            .start()
        server = process

        // 'use' closes stdin after the handshake — the EOF that must end the server.
        process.outputStream.bufferedWriter().use { stdin ->
            stdin.write(
                """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18",""" +
                    """"capabilities":{},"clientInfo":{"name":"test","version":"0"}}}"""
            )
            stdin.newLine()
            stdin.flush()

            val response = try {
                CompletableFuture.supplyAsync { process.inputStream.bufferedReader().readLine() }
                    .get(20, TimeUnit.SECONDS)
            } catch (e: TimeoutException) {
                fail("No 'initialize' response within 20 s (server alive: ${process.isAlive})")
            }
            assertTrue(
                response.orEmpty().contains("\"id\":1"),
                "No 'initialize' response, got: $response"
            )
        }

        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            fail("MCP server JVM still running 30 s after its stdin was closed")
        }
        assertEquals(
            0, process.exitValue(),
            "MCP server exited non-cleanly on stdin EOF (stderr: ${stderrLog.toFile().readText().take(2000)})"
        )
    }
}
