/*
 * Copyright 2024-2026 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

@file:JvmName("ComposeHotReloadMcp")

package org.jetbrains.compose.reload.mcp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible
import org.jetbrains.compose.reload.InternalHotReloadApi
import org.jetbrains.compose.reload.core.HotReloadEnvironment
import org.jetbrains.compose.reload.core.PidFileInfo
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.error
import org.jetbrains.compose.reload.core.getOrNull
import org.jetbrains.compose.reload.core.getOrThrow
import org.jetbrains.compose.reload.core.info
import org.jetbrains.compose.reload.core.isFailure
import org.jetbrains.compose.reload.core.warn
import org.jetbrains.compose.reload.orchestration.OrchestrationClientRole
import org.jetbrains.compose.reload.orchestration.OrchestrationHandle
import org.jetbrains.compose.reload.orchestration.connectOrchestrationClient
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import kotlin.io.path.isRegularFile
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.seconds

private val logger = createLogger()

@OptIn(InternalHotReloadApi::class)
fun main(args: Array<String>) {
    val pidFile = HotReloadEnvironment.pidFile
        ?: args.firstOrNull()?.let { Path.of(it) }
        ?: run {
            logger.error("PID file path not provided. Set it via -Dcompose.reload.pidFile=PATH or pass as argument")
            exitProcess(1)
        }

    logger.info("MCP server starting. Watching PID file: $pidFile")

    runBlocking {
        val orchestration = connectionLoop(pidFile)
            .stateIn(this, SharingStarted.Eagerly, null)

        startMcpServer(orchestration)
    }
}

/**
 * Returns a [Flow] that emits the current [OrchestrationHandle] when connected
 * and `null` when disconnected. Continuously watches the PID file for an orchestration port,
 * connects to the orchestration server, and reconnects when the connection drops.
 */
internal fun connectionLoop(pidFile: Path): Flow<OrchestrationHandle?> = flow {
    while (true) {
        val port = waitForOrchestrationPort(pidFile)
        logger.info("Found orchestration port $port, connecting...")

        val result = connectOrchestrationClient(OrchestrationClientRole.Tooling, port)
        if (result.isFailure()) {
            logger.warn("Failed to connect to orchestration on port $port, will retry...")
            delay(2.seconds)
            continue
        }

        val client = result.getOrThrow()
        logger.info("Connected to orchestration on port $port")
        emit(client)

        // Suspend until the connection closes (app exit)
        client.await()

        logger.info("Orchestration connection closed. Waiting for new application...")
        emit(null)
    }
}

/**
 * Watches the PID file directory until an orchestration port is found.
 */
internal suspend fun waitForOrchestrationPort(pidFile: Path): Int {
    val directory = pidFile.parent
    val fileName = pidFile.fileName

    readOrchestrationPort(pidFile)?.let { return it }

    directory.fileSystem.newWatchService().use { watchService ->
        directory.register(watchService, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY)

        // Re-check after registration to avoid a race where the file appeared
        // between the initial read and the WatchService registration
        readOrchestrationPort(pidFile)?.let { return it }

        while (true) {
            val key = runInterruptible(Dispatchers.IO) { watchService.take() }
            for (event in key.pollEvents()) {
                if ((event.context() as? Path) == fileName) {
                    readOrchestrationPort(pidFile)?.let { return it }
                }
            }
            key.reset()
        }
    }
}

private fun readOrchestrationPort(pidFile: Path): Int? {
    if (!pidFile.isRegularFile()) return null
    return PidFileInfo(pidFile).getOrNull()?.orchestrationPort
}
