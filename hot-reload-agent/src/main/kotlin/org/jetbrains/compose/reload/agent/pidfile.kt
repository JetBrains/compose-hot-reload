/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.agent

import org.jetbrains.compose.reload.core.HotReloadEnvironment
import org.jetbrains.compose.reload.core.PidFileInfo
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.debug
import org.jetbrains.compose.reload.core.deleteMyPidFileIfExists
import org.jetbrains.compose.reload.core.getBlocking
import org.jetbrains.compose.reload.core.getOrThrow
import org.jetbrains.compose.reload.core.runDirectoryLockFile
import org.jetbrains.compose.reload.core.writePidFile
import kotlin.io.path.createParentDirectories

private val logger = createLogger()

internal fun createPidfile() {
    val pidFile = HotReloadEnvironment.pidFile ?: return

    val myPidFileInfo = PidFileInfo(
        pid = ProcessHandle.current().pid(),
        orchestrationPort = orchestration.port.getBlocking().getOrThrow()
    )

    runDirectoryLockFile?.withLock {
        pidFile.createParentDirectories().writePidFile(myPidFileInfo)
        logger.debug("Created pid file: ${pidFile.toUri()}")
    }

    Runtime.getRuntime().addShutdownHook(Thread {
        runDirectoryLockFile?.withLock {
            pidFile.deleteMyPidFileIfExists(myPidFileInfo)
        }
    })
}
