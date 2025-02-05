/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.agent

import org.jetbrains.compose.reload.core.HotReloadEnvironment
import org.jetbrains.compose.reload.core.createLogger
import java.util.Properties
import kotlin.io.path.createParentDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.outputStream

private val logger = createLogger()

internal fun createPidfile() {
    val pidFile = HotReloadEnvironment.pidFile ?: return
    val properties = Properties()
    properties["pid"] = ProcessHandle.current().pid().toString()
    properties["orchestration.port"] = orchestration.port.toString()

    pidFile.createParentDirectories().outputStream().use { out ->
        properties.store(out, null)
    }

    logger.info("Created pid file: ${pidFile.toUri()}")
    Runtime.getRuntime().addShutdownHook(Thread {
        pidFile.deleteIfExists()
    })
}
