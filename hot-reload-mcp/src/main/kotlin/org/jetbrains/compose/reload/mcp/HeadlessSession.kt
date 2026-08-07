/*
 * Copyright 2024-2026 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.mcp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.devtools.api.VirtualTimeState
import org.jetbrains.compose.reload.InternalHotReloadApi
import org.jetbrains.compose.reload.core.HotReloadEnvironment
import org.jetbrains.compose.reload.core.HotReloadProperty
import org.jetbrains.compose.reload.core.awaitOrThrow
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.destroyWithDescendants
import org.jetbrains.compose.reload.core.info
import org.jetbrains.compose.reload.core.warn
import org.jetbrains.compose.reload.orchestration.OrchestrationClientRole.Application
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.Ack
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ClientDisconnected
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.CriticalException
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.Ping
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ShutdownRequest
import org.jetbrains.compose.reload.orchestration.OrchestrationMessageId
import org.jetbrains.compose.reload.orchestration.OrchestrationServer
import org.jetbrains.compose.reload.orchestration.asChannel
import org.jetbrains.compose.reload.orchestration.startOrchestrationServer
import java.io.File
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.absolutePathString
import kotlin.io.path.createTempFile
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private val logger = createLogger()

/**
 * Things required by the MCP server to launch a headless `DevApplication`.
 *
 * @param javaBinary the JBR launcher to spawn.
 * @param argFile a JVM '@argfile' carrying the hot-reload JVM arguments (agent, DCEVM flag, hot
 * classpath, recompiler properties), the full runtime `-cp`, and the
 * `org.jetbrains.compose.reload.jvm.DevApplication` main class. The application's `--className` /
 * `--funName` (and optional `--width` / `--height`) are appended per session by [HeadlessSession].
 */
@InternalHotReloadApi
data class HeadlessLaunchSpec(
    val javaBinary: Path,
    val argFile: Path,
) {
    companion object {
        /**
         * Reads the launch spec from the [HotReloadProperty.HeadlessJavaBinary] /
         * [HotReloadProperty.HeadlessArgFile] system properties set by the Gradle plugin.
         */
        fun fromSystemProperties(): HeadlessLaunchSpec? {
            val javaBinary = HotReloadEnvironment.headlessJavaBinary ?: return null
            val argFile = HotReloadEnvironment.headlessArgFile ?: return null
            return HeadlessLaunchSpec(javaBinary, argFile)
        }
    }
}

/**
 * A single headless application spawned by [HeadlessSessionManager]. The MCP server hosts a
 * dedicated [OrchestrationServer] per session; the forked JVM connects to it as an
 * [OrchestrationClientRole.Application] client.
 */
@InternalHotReloadApi
class HeadlessSession(
    val id: String,
    val orchestration: OrchestrationServer,
    private val process: Process,
    private val logFile: File,
) {
    val isAlive: Boolean get() = process.isAlive

    /**
     * Requests a graceful shutdown of the application, then forcibly destroys the process tree if it
     * does not exit within [timeout], closes the hosted orchestration server, and removes the
     * captured process log.
     */
    suspend fun close(timeout: Duration = 10.seconds) {
        logger.info { "headless[$id]: closing" }
        try {
            orchestration.send(ShutdownRequest("MCP headless session '$id' closed"))
        } catch (t: Throwable) {
            logger.warn("headless[$id]: failed to send ShutdownRequest: ${t.message}")
        }

        val exited = runInterruptible(Dispatchers.IO) {
            process.waitFor(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
        }
        if (!exited) {
            logger.warn("headless[$id]: process still alive after $timeout, destroying")
            process.destroyWithDescendants()
        }
        orchestration.close()
        runCatching { logFile.delete() }
    }
}

/**
 * Registry of headless [HeadlessSession]s. Each [start] hosts a fresh orchestration server and forks
 * a [HeadlessLaunchSpec]-described JVM running the requested `@Composable`. Sessions are addressed by
 * the string id returned from [start].
 */
@InternalHotReloadApi
class HeadlessSessionManager(private val launchSpec: HeadlessLaunchSpec?) {
    private val sessions = ConcurrentHashMap<String, HeadlessSession>()
    private val counter = AtomicInteger()

    val isSupported: Boolean get() = launchSpec != null

    operator fun get(id: String): HeadlessSession? = sessions[id]

    /**
     * Hosts an orchestration server, forks the headless application for [className].[funName]
     * (optionally sized [width]x[height]; non-positive values let the scene auto-measure), and
     * suspends until the application connects or [connectTimeout] elapses.
     *
     * @throws IllegalStateException when there is no headless support (no [launchSpec]).
     */
    suspend fun start(
        className: String,
        funName: String,
        width: Int = 0,
        height: Int = 0,
        connectTimeout: Duration = 60.seconds,
    ): HeadlessSession {
        val spec = launchSpec
            ?: error(
                "Headless mode is not available: the MCP server was started without a headless launch spec." +
                    " Ensure it was launched via the 'hotMcpServer' Gradle task."
            )

        val id = "headless-${counter.incrementAndGet()}"
        val server = startOrchestrationServer()
        val port = server.port.awaitOrThrow()
        logger.info { "headless[$id]: hosting orchestration server on port $port for $className.$funName" }

        val messages = server.asChannel().consumeAsFlow()

        val logFile = createTempFile("chr-headless-$id-", ".log").toFile()
        logFile.deleteOnExit()
        val command = buildList {
            add(spec.javaBinary.absolutePathString())
            add("-D${HotReloadProperty.OrchestrationPort.key}=$port")
            add("@${spec.argFile.absolutePathString()}")
            add("--className"); add(className)
            add("--funName"); add(funName)
            if (width > 0) { add("--width"); add(width.toString()) }
            if (height > 0) { add("--height"); add(height.toString()) }
        }
        logger.info { "headless[$id]: launching ${command.joinToString(" ")}" }

        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .redirectOutput(logFile)
            .start()

        val session = HeadlessSession(id, server, process, logFile)
        sessions[id] = session

        try {
            awaitReady(id, server, process, logFile, "$className.$funName", messages, connectTimeout)
        } catch (t: Throwable) {
            sessions.remove(id)
            runCatching { session.close() }
            throw t
        }

        logger.info { "headless[$id]: application ready" }
        return session
    }

    /**
     * Suspends until the forked application's render loop is alive.
     */
    private suspend fun awaitReady(
        id: String,
        server: OrchestrationServer,
        process: Process,
        logFile: File,
        target: String,
        messages: Flow<OrchestrationMessage>,
        timeout: Duration,
    ) {
        val pingIds = ConcurrentHashMap.newKeySet<OrchestrationMessageId>()

        // Freeze virtual time. Consider to unfeeze it when we support animations.
        server.update(VirtualTimeState) { VirtualTimeState(Duration.ZERO) }

        val ready = withTimeoutOrNull(timeout) {
            coroutineScope {
                val ticker = launch {
                    while (isActive) {
                        // send ping in a loop until the app connects to the orhestarion server
                        // and replies with Ack.
                        val ping = Ping()
                        pingIds.add(ping.messageId)
                        runCatching { server.send(ping) }
                        delay(500.milliseconds)
                    }
                }
                try {
                    messages.firstOrNull { message ->
                        when {
                            message is CriticalException && message.clientRole == Application ->
                                throw HeadlessStartException(
                                    "The headless application '$target' crashed on startup: " +
                                        "${message.exceptionClassName}: ${message.message}"
                                )

                            message is ClientDisconnected && message.clientRole == Application ->
                                throw HeadlessStartException(
                                    "The headless application '$target' exited during startup.\n${logTail(logFile)}"
                                )

                            message is Ack && message.acknowledgedMessageId in pingIds -> true
                            else -> false
                        }
                    }
                } finally {
                    ticker.cancel()
                }
            }
        }

        if (ready == null) {
            if (!process.isAlive) throw HeadlessStartException(
                "The headless application '$target' exited during startup.\n${logTail(logFile)}"
            )
            throw HeadlessStartException(
                "Timed out after $timeout waiting for the headless application '$target' to become " +
                    "ready.\n${logTail(logFile)}"
            )
        }
    }

    /** Closes and removes the session with [id]; returns `false` if no such session exists. */
    suspend fun close(id: String): Boolean {
        val session = sessions.remove(id) ?: return false
        session.close()
        return true
    }

    /** Closes every live session (best-effort); used on MCP server shutdown. */
    suspend fun closeAll() {
        sessions.keys.toList().forEach { id -> runCatching { close(id) } }
    }

    fun ids(): List<String> = sessions.keys.toList()
}

/** Thrown by [HeadlessSessionManager.start] when the forked application fails to become ready. */
@InternalHotReloadApi
class HeadlessStartException(message: String) : Exception(message)

/** Returns a bounded tail of the forked process' captured output for inclusion in error messages. */
private fun logTail(logFile: File, maxChars: Int = 2000): String {
    val text = runCatching { logFile.readText() }.getOrNull().orEmpty()
    if (text.isBlank()) return "(no process output was captured)"
    val tail = if (text.length > maxChars) "…" + text.takeLast(maxChars) else text
    return "--- application output ---\n$tail"
}
