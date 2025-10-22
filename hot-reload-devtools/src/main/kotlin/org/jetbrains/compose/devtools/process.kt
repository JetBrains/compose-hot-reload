/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.devtools

import org.jetbrains.compose.reload.core.HotReloadEnvironment
import org.jetbrains.compose.reload.core.HotReloadProperty
import org.jetbrains.compose.reload.core.PidFileInfo
import org.jetbrains.compose.reload.core.awaitIdle
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.debug
import org.jetbrains.compose.reload.core.deleteMyPidFileIfExists
import org.jetbrains.compose.reload.core.error
import org.jetbrains.compose.reload.core.invokeOnCompletion
import org.jetbrains.compose.reload.core.launchTask
import org.jetbrains.compose.reload.core.reloadMainThread
import org.jetbrains.compose.reload.core.runDirectoryLockFile
import org.jetbrains.compose.reload.core.warn
import org.jetbrains.compose.reload.orchestration.OrchestrationClientRole
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import kotlin.concurrent.thread
import kotlin.jvm.optionals.getOrNull

private val logger = createLogger()

internal fun setupDevToolsProcess() {
    setupExceptionHandler()
    setupShutdownProcedure()
    setupOrchestration()
    bindParentProcess()
}

private fun setupExceptionHandler() {
    Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
        logger.error("Uncaught exception in thread '${thread.name}': ${exception.message}", exception)
        launchTask {
            logger.error("Initiating shutdown because of uncaught exception: ${exception.javaClass.simpleName}")
            OrchestrationMessage.CriticalException(OrchestrationClientRole.Tooling, exception).sendAsync()
            reloadMainThread.awaitIdle()
            shutdown()
        }
    }
}

private fun bindParentProcess() {
    val pid = HotReloadEnvironment.parentPid ?: run {
        logger.error("parentPid: Missing")
        shutdown()
    }

    if (HotReloadEnvironment.pidFile == null) {
        logger.warn("parentPid: Missing '${HotReloadProperty.PidFile.key}' property")
    }

    val process = ProcessHandle.of(pid).getOrNull() ?: run {
        logger.error("parentPid: Cannot find process with pid=$pid")
        shutdown()
    }

    thread {
        process.onExit().get()
        logger.debug("parentPid: Parent process with pid=$pid exited")
        runCatching {
            runDirectoryLockFile?.withLock {
                HotReloadEnvironment.pidFile?.deleteMyPidFileIfExists(
                    PidFileInfo(pid = pid, orchestrationPort = orchestration.port.getOrNull()?.leftOrNull())
                )
            }
        }
        shutdown()
    }
}

private fun setupOrchestration() {
    orchestration.invokeOnCompletion {
        shutdown()
    }
}
