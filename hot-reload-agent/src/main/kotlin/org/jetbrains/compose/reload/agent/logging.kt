/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.agent

import org.jetbrains.compose.reload.core.HotReloadEnvironment
import org.jetbrains.compose.reload.core.Logger
import org.jetbrains.compose.reload.core.Queue
import org.jetbrains.compose.reload.core.WorkerThread
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.debug
import org.jetbrains.compose.reload.core.displayString
import org.jetbrains.compose.reload.core.invokeOnFinish
import org.jetbrains.compose.reload.core.invokeOnStop
import org.jetbrains.compose.reload.core.launchOnFinish
import org.jetbrains.compose.reload.core.launchTask
import org.jetbrains.compose.reload.core.withThread
import org.jetbrains.compose.reload.core.withType
import org.jetbrains.compose.reload.orchestration.OrchestrationHandle
import org.jetbrains.compose.reload.orchestration.OrchestrationLoggerState
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.CriticalException
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.LogMessage
import org.jetbrains.compose.reload.orchestration.toMessage
import java.time.LocalDateTime
import kotlin.io.path.createParentDirectories
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.outputStream

private val logger = createLogger()

private val loggerThread = WorkerThread("Logger")

private val outgoingLoggingQueue: Queue<Logger.Log> = Queue()

private val incomingLoggingQueue: Queue<Logger.Log> = Queue()

internal class AgentLoggerDispatch : Logger.Dispatch {
    override fun add(log: Logger.Log) {
        outgoingLoggingQueue.add(log)
    }
}

internal fun OrchestrationHandle.startDispatchingLogs() {
    subtask {
        orchestration.messages.withType<CriticalException>().collect { exception ->
            val message = LogMessage(
                environment = null,
                loggerName = "<<CRITICAL>>",
                threadName = "<<UNKNOWN>>",
                timestamp = System.currentTimeMillis(),
                level = Logger.Level.Error,
                message = "Received critical exception: ${exception.message}",
                throwableClassName = exception.exceptionClassName,
                throwableMessage = exception.message,
                throwableStacktrace = exception.stacktrace.toList()
            )

            incomingLoggingQueue.add(message)
        }
    }

    subtask {
        subtask {
            val loggerId = OrchestrationLoggerState.LoggerId.create()
            update(OrchestrationLoggerState) { state -> state.withLogger(loggerId) }
            launchOnFinish {
                update(OrchestrationLoggerState) { state -> state.withoutLogger(loggerId) }
            }
        }

        orchestration.messages.withType<LogMessage>().collect { message ->
            incomingLoggingQueue.add(message)
        }
    }

    subtask {
        while (true) {
            val log = outgoingLoggingQueue.receive()
            send(log.toMessage())
        }
    }
}

internal fun startWritingLogs() = launchTask task@{
    /* Create 'Hello' statements */
    logger.debug("Compose Hot Reload: Run at ${LocalDateTime.now()}")
    logger.debug("Compose Hot Reload: PID: ${ProcessHandle.current().pid()}")
    logger.debug("Compose Hot Reload: Orchestration port: ${orchestration.port.await()}")

    HotReloadEnvironment::class.java.declaredMethods
        .filter { it.name.startsWith("get") }
        .filter { it.parameterCount == 0 }
        .forEach { method ->
            val key = "${HotReloadEnvironment::class.java.simpleName}.${method.name}"
            val value = method.invoke(HotReloadEnvironment)
            logger.debug("$key = $value")
        }

    withThread(loggerThread) {
        val pidFile = HotReloadEnvironment.pidFile
        val logFile = pidFile?.resolveSibling(pidFile.nameWithoutExtension + ".chr.log")
        val logFileWriter = logFile?.createParentDirectories()?.outputStream()?.bufferedWriter()
        invokeOnFinish { logFileWriter?.close() }
        invokeOnStop { logFileWriter?.close() }

        while (true) {
            val message = incomingLoggingQueue.receive()
            if (HotReloadEnvironment.logStdout) {
                println(message.displayString())
            }

            logFileWriter?.appendLine(message.displayString(useEffects = false))
            logFileWriter?.flush()
        }
    }
}
