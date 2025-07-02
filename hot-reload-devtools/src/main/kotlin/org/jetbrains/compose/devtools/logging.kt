/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.devtools

import org.jetbrains.compose.reload.core.Logger
import org.jetbrains.compose.reload.core.Queue
import org.jetbrains.compose.reload.orchestration.OrchestrationHandle
import org.jetbrains.compose.reload.orchestration.toMessage

internal val devtoolsLoggingQueue = Queue<Logger.Log>()

internal class DevToolsLoggerDispatch : Logger.Dispatch {
    override fun add(log: Logger.Log) {
        devtoolsLoggingQueue.add(log)
    }
}

internal fun OrchestrationHandle.startLoggingDispatch() = subtask {
    while (true) {
        val log = devtoolsLoggingQueue.receive()
        try {
            send(log.toMessage())
        } catch (t: Throwable) {
            /* Re-Dispatch log */
            devtoolsLoggingQueue.send(log)
            throw t
        }
    }
}
