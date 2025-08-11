/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.devtools

import org.jetbrains.compose.reload.core.HotReloadEnvironment
import org.jetbrains.compose.reload.core.HotReloadProperty
import org.jetbrains.compose.reload.core.PidFileInfo
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.deleteMyPidFileIfExists
import org.jetbrains.compose.reload.core.error
import org.jetbrains.compose.reload.core.info
import org.jetbrains.compose.reload.core.invokeOnCompletion
import org.jetbrains.compose.reload.core.runDirectoryLockFile
import org.jetbrains.compose.reload.core.warn
import kotlin.concurrent.thread
import kotlin.jvm.optionals.getOrNull

private val logger = createLogger()

internal fun setupDevToolsProcess() {
    setupShutdownProcedure()
    setupOrchestration()
    bindParentProcess()
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
        logger.info("parentPid: Parent process with pid=$pid exited")
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
