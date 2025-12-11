/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.test.gradle

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import org.jetbrains.compose.reload.InternalHotReloadApi
import org.jetbrains.compose.reload.core.Environment
import org.jetbrains.compose.reload.core.Logger
import org.jetbrains.compose.reload.core.Queue
import org.jetbrains.compose.reload.core.asFileName
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.info
import org.jetbrains.compose.reload.orchestration.OrchestrationHandle
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.LogMessage
import org.jetbrains.compose.reload.orchestration.OrchestrationServer
import org.jetbrains.compose.reload.orchestration.asChannel
import org.jetbrains.compose.reload.orchestration.toMessage
import org.jetbrains.kotlin.tooling.core.Extras
import org.jetbrains.kotlin.tooling.core.extrasKeyOf
import org.junit.jupiter.api.extension.ExtensionContext
import java.io.BufferedWriter
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.bufferedWriter
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists

/*
Logging in Tests:
Multiple processes / components do run together when tests are launched.
We will collect the output/logs through the orchestration and store them in log files.
The scope can exceed the scope of the test to ensure logging continues even after the test has officially failed
or finished.
 */

private val testLoggingScope = CoroutineScope(
    Job() + Dispatchers.IO + CoroutineName("Orchestration Test Logging") + CoroutineExceptionHandler { ctx, e ->
        e.printStackTrace()
    }
)

@OptIn(ExperimentalPathApi::class)
internal fun ExtensionContext.startOrchestrationTestLogging(server: OrchestrationServer) = testLoggingScope.launch {
    val testClass = requiredTestClass
    val testMethod = requiredTestMethod
    val context = hotReloadTestInvocationContextOrThrow
    val job = currentCoroutineContext().job

    val outputDirectory = hotReloadLogsDirectory

    /* Clean previous logs if present */
    if (outputDirectory.exists()) outputDirectory.deleteRecursively()
    outputDirectory.createDirectories()

    /* We put all log messages into the logs.txt */
    val allLogs = outputDirectory.resolve("logs.txt")
    val allLogsWriter = allLogs.bufferedWriter()
    job.invokeOnCompletion {
        allLogsWriter.close()
    }

    /* We also write all messages into the messages.txt */
    val allMessages = outputDirectory.resolve("messages.txt")
    val allMessagesWriter = allMessages.bufferedWriter()
    job.invokeOnCompletion {
        allMessagesWriter.close()
    }


    allLogsWriter.appendLine("<<Start>>")
    allMessagesWriter.appendLine("<<Start>>")

    /* We will create a specific log file for each log tag (e.g., a logs-Compiler.txt) */
    val taggedWriters = hashMapOf<Environment?, BufferedWriter>()
    fun taggedWriter(tag: Environment?): BufferedWriter = taggedWriters.getOrPut(tag) {
        val file = outputDirectory.resolve("logs-$tag.txt".asFileName())
        val writer = file.bufferedWriter()
        writer.appendLine("<<Start>>")
        job.invokeOnCompletion {
            writer.close()
        }

        writer
    }

    val testClassLogger = createLogger(testClass.name)
    testClassLogger.info(
        """
        ${requiredTestMethod.name} (${context.getDisplayName()})
        logs: ${allLogs.toUri()}
        messages: ${allMessages.toUri()}
    """.trimIndent()
    )

    /* We collect all messages and log them: The flow will be closed when the orchestration closes */
    server.asChannel().consumeAsFlow().collect { message ->
        val messageString = message.toString()
        if (message is LogMessage) {
            allLogsWriter.appendLine(messageString)
            allLogsWriter.flush()
            val environment = message.environment
            if (environment != null) {
                val writer = taggedWriter(environment)
                writer.appendLine(messageString)
                writer.flush()
            }
        }

        allMessagesWriter.appendLine(messageString)
        allMessagesWriter.flush()
    }

    /* Flush and close all writers */
    allLogsWriter.appendLine("<<End>>")
    allLogsWriter.flush()
    allLogsWriter.close()

    allMessagesWriter.appendLine("<<End>>")
    allMessagesWriter.flush()
    allMessagesWriter.close()

    taggedWriters.forEach { (_, writer) ->
        writer.appendLine("<<End>>")
        writer.flush()
        writer.close()
    }
}

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

internal val ExtensionContext.hotReloadLogsDirectory: Path get() = Path("build/logs/${requiredTestClass.name.asFileName()}")
    .resolve(requiredTestMethod.name.asFileName())
    .resolve(displayName.asFileName().replace("(", "").replace(")", ""))

internal data class SaveExecutionLogs(
    val outputDir: Path,
) {
    companion object {
        val key: Extras.Key<SaveExecutionLogs> = extrasKeyOf()
    }
}