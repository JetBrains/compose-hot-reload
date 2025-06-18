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
import org.jetbrains.compose.reload.core.Environment
import org.jetbrains.compose.reload.core.asFileName
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.info
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.LogMessage
import org.jetbrains.compose.reload.orchestration.OrchestrationServer
import org.jetbrains.compose.reload.orchestration.asChannel
import org.junit.jupiter.api.extension.ExtensionContext
import java.io.BufferedWriter
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

    val outputDirectory = Path("build/logs/${testClass.name.asFileName()}")
        .resolve(testMethod.name.asFileName())
        .resolve(context.getDisplayName().asFileName().replace("(", "").replace(")", ""))

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
                taggedWriter(environment).appendLine(messageString)
            }
        }

        allMessagesWriter.appendLine(messageString)
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
