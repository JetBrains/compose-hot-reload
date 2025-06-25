/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.devtools

import org.jetbrains.compose.reload.core.Disposable
import org.jetbrains.compose.reload.core.HotReloadEnvironment
import org.jetbrains.compose.reload.core.Try
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.error
import org.jetbrains.compose.reload.core.exception
import org.jetbrains.compose.reload.core.isFailure
import org.jetbrains.compose.reload.core.isSuccess
import org.jetbrains.compose.reload.core.launchTask
import org.jetbrains.compose.reload.orchestration.OrchestrationClientRole
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.CriticalException
import java.nio.file.Path
import java.util.concurrent.LinkedBlockingQueue
import kotlin.io.path.bufferedWriter
import kotlin.io.path.createParentDirectories
import kotlin.io.path.exists
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.milliseconds

private val logger = createLogger()

internal fun setupShutdownProcedure() {
    Runtime.getRuntime().addShutdownHook(ShutdownThread)

    /* Log the shutdown */
    invokeOnShutdown {
        logger.error("Shutting down...")
    }

    /* By default, Uncaught exceptions shall result in a shutdown */
    Thread.setDefaultUncaughtExceptionHandler { thread, e ->
        logger.error("Uncaught exception in thread '${thread.name}': ${e.message}", e)

        launchTask {
            CriticalException(
                clientRole = OrchestrationClientRole.Tooling,
                message = "Uncaught Exception in ${thread.name}: ${e.message}",
                exceptionClassName = e.javaClass.name,
                stacktrace = e.stackTrace.toList()
            ).send()

            shutdown()
        }
    }
}

internal fun shutdown(): Nothing {
    exitProcess(0)
}

internal fun invokeOnShutdown(action: () -> Unit): Disposable {
    val action = ShutdownAction(work = action)
    ShutdownThread.actions.add(action)
    return Disposable { ShutdownThread.actions.remove(action) }
}

/*
Single Shutdown Thread implementation below
 */

private class ShutdownAction(
    val work: () -> Unit,
    val registration: Array<StackTraceElement> = Thread.currentThread().stackTrace
)

private val shutdownReportFile: Path? =
    HotReloadEnvironment.pidFile?.resolveSibling("shutdown.log")

private fun shutdownErrorReportFile(): Path? {
    val pidFile = HotReloadEnvironment.pidFile ?: return null
    repeat(128) { index ->
        val reportFile = pidFile.resolveSibling("shutdown-error.$index.log")
        if (!reportFile.exists()) return reportFile
    }
    return null
}

private object ShutdownThread : Thread("shutdown") {
    val actions = LinkedBlockingQueue<ShutdownAction>()

    override fun run() {
        val startInstant = System.currentTimeMillis()
        val shutdownReportWriter = shutdownReportFile?.createParentDirectories()?.bufferedWriter()
        shutdownReportWriter?.appendLine("Shutting down...")
        shutdownReportWriter?.appendLine("Shutdown actions registered: ${actions.size}")
        shutdownReportWriter?.flush()

        var totalFailures = 0
        var totalSuccess = 0

        while (actions.isNotEmpty()) {
            val action = actions.poll() ?: continue
            val result = Try { action.work() }

            if (result.isSuccess()) {
                totalSuccess++
            }

            if (result.isFailure()) {
                totalFailures++
                logShutdownActionFailure(action, result.exception)
            }

        }

        val endInstant = System.currentTimeMillis()
        val duration = (endInstant - startInstant).milliseconds
        shutdownReportWriter?.appendLine("Shutdown actions completed: $totalSuccess, failed: $totalFailures")
        shutdownReportWriter?.appendLine("Shutdown duration: $duration")
        shutdownReportWriter?.close()
    }


    private fun logShutdownActionFailure(action: ShutdownAction, exception: Throwable) {
        runCatching {
            shutdownErrorReportFile()?.createParentDirectories()?.bufferedWriter()?.use { writer ->
                writer.appendLine("Failed to execute shutdown action:")
                writer.appendLine(exception.message)
                exception.stackTrace.forEach { element ->
                    writer.appendLine("  at $element")
                }

                if (exception.suppressedExceptions.isNotEmpty()) writer.appendLine()
                exception.suppressedExceptions.forEach { suppressed ->
                    writer.appendLine("Suppressed:")
                    writer.appendLine(suppressed.message)
                    suppressed.stackTrace.forEach { element ->
                        writer.appendLine("  at $element")
                    }
                }

                writer.appendLine()
                writer.appendLine("Registration:")
                action.registration.forEach { element ->
                    writer.appendLine("    at $element")
                }
            }
        }.onFailure { e -> e.printStackTrace() }
    }
}
