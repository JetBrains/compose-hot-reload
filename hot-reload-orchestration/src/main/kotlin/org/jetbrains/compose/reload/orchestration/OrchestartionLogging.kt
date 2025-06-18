/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.orchestration

import org.jetbrains.compose.reload.core.Environment
import org.jetbrains.compose.reload.core.InternalHotReloadApi
import org.jetbrains.compose.reload.core.Logger
import org.jetbrains.compose.reload.core.Queue

@InternalHotReloadApi
public fun Logger.Log.toMessage(): OrchestrationMessage.LogMessage {
    return OrchestrationMessage.LogMessage(
        environment = this.environment,
        loggerName = this.loggerName,
        threadName = this.threadName,
        timestamp = this.timestamp,
        level = this.level,
        message = this.message,
        throwableClassName = this.throwableClassName,
        throwableMessage = this.throwableMessage,
        throwableStacktrace = this.throwableStacktrace,
    )
}

public fun LogMessage(
    message: String,
    environment: Environment? = Environment.current,
    loggerName: String? = null,
    threadName: String? = null,
    timestamp: Long = System.currentTimeMillis(),
    level: Logger.Level = Logger.Level.Debug,
    throwableClassName: String? = null,
    throwableMessage : String? = null,
    throwableStacktrace: List<StackTraceElement>? = null
): OrchestrationMessage.LogMessage = OrchestrationMessage.LogMessage(
    message = message,
    environment = environment,
    loggerName = loggerName,
    threadName = threadName,
    timestamp = timestamp,
    level = level,
    throwableClassName = throwableClassName,
    throwableMessage = throwableMessage,
    throwableStacktrace = throwableStacktrace,
)
@InternalHotReloadApi
public fun OrchestrationHandle.startLoggerDispatch(): Logger.Dispatch {
    val queue = Queue<Logger.Log>()

    subtask("loggerDispatch") {
        while (true) {
            val log = queue.receive()
            send(log.toMessage())
        }
    }

    return Logger.Dispatch { log ->
        queue.add(log)
    }
}
