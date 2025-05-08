/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.test.gradle

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.intellij.lang.annotations.Language
import org.jetbrains.compose.reload.core.AsyncTraces
import org.jetbrains.compose.reload.core.asyncTracesString
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.withAsyncTrace
import org.jetbrains.compose.reload.orchestration.OrchestrationClientRole
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.Ack
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ClientDisconnected
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.LogMessage
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.Ping
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ReloadClassesRequest
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ReloadClassesResult
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.UIRendered
import org.jetbrains.compose.reload.orchestration.asFlow
import org.slf4j.Logger
import java.nio.file.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.fail
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@DslMarker
public annotation class TransactionDslMarker

@TransactionDslMarker
public class TransactionScope internal constructor(
    @PublishedApi
    internal val fixture: HotReloadTestFixture,
    private val coroutineScope: CoroutineScope,
    @PublishedApi
    internal val incomingMessages: ReceiveChannel<OrchestrationMessage>,
) : CoroutineScope by coroutineScope {

    @PublishedApi
    internal val logger: Logger = createLogger()

    public fun OrchestrationMessage.send() {
        fixture.orchestration.sendMessage(this).get()
    }

    public suspend fun launchChildTransaction(block: suspend TransactionScope.() -> Unit): Job {
        val receiveChannel = fixture.createReceiveChannel()

        return launch(AsyncTraces("launchChildTransaction")) {
            TransactionScope(fixture, this@launch, receiveChannel).block()
        }
    }

    /**
     * Collects all currently available messages into a list
     */
    public fun pullMessages(): List<OrchestrationMessage> = buildList {
        while (true) {
            incomingMessages.tryReceive().getOrNull()?.let(::add) ?: break
        }
    }

    public suspend inline fun <reified T> skipToMessage(
        title: String = "Waiting for message '${T::class.simpleName.toString()}'",
        timeout: Duration = 5.minutes,
        crossinline filter: (T) -> Boolean = { true }
    ): T = withAsyncTrace("'skipToMessage($title)'") {
        val dispatcher = Dispatchers.Default.limitedParallelism(1)
        val asyncTracesString = asyncTracesString()

        val reminder = fixture.daemonTestScope.launch(dispatcher) {
            val sleep = 15.seconds
            var waiting = 0.seconds
            while (true) {
                logger.info(
                    "'$title' ($waiting/$timeout)\n" +
                        asyncTracesString.prependIndent("\t")
                )
                delay(sleep)
                waiting += sleep
            }
        }

        /* Monitor the application (might crash and disconnect) */
        val applicationMonitor = launch {
            // No need to monitor if the skip is actually skipping to this message anyway
            if (T::class == ClientDisconnected::class) return@launch
            fixture.orchestration.asFlow().filterIsInstance<ClientDisconnected>().collect { message ->
                if (message.clientRole == OrchestrationClientRole.Application) {
                    logger.info("'$title': Application disconnected.")
                    fail("Application disconnected. $asyncTracesString")
                }
            }
        }

        withContext(dispatcher) {
            try {
                withTimeout(if (fixture.isDebug) 24.hours else timeout) {
                    incomingMessages.receiveAsFlow().filterIsInstance<T>().filter(filter).first()
                }
            } finally {
                reminder.cancel()
                applicationMonitor.cancel()
            }
        }
    }

    public suspend fun launchApplicationAndWait(
        projectPath: String = ":",
        mainClass: String = "MainKt",
    ): Unit = withAsyncTrace("'launchApplicationAndWait'") {
        fixture.launchApplication(projectPath, mainClass)
        var uiRendered = false

        skipToMessage<OrchestrationMessage>("Waiting for application to start") { message ->
            if (message is UIRendered) uiRendered = true
            if (message is ClientDisconnected && message.clientRole == OrchestrationClientRole.Application) {
                fail("Application disconnected")
            }
            if (message !is LogMessage && message !is Ack && message !is Ping) {
                logger.debug("application startup: received message: $message")
                logger.debug("application startup: uiRendered=$uiRendered")
            }
            uiRendered
        }
    }

    public suspend fun launchDevApplicationAndWait(
        projectPath: String = ":",
        className: String,
        funName: String
    ): Unit = withAsyncTrace("'launchDevApplicationAndWait'") {
        fixture.launchDevApplication(projectPath, className, funName)
        skipToMessage<UIRendered>("Waiting for dev application to start")
    }

    public suspend fun sync(): Unit = withAsyncTrace("'sync'") {
        val ping = Ping()
        ping.send()
        awaitAck(ping)
    }

    public suspend fun awaitAck(message: OrchestrationMessage): Unit = withAsyncTrace("'awaitAck'") run@{
        /*
        There is no ACK for an ACK, and there is also no ACk for a shutdown request.
         */
        if (message is Ack || message is OrchestrationMessage.ShutdownRequest) return@run
        skipToMessage<Ack>("Waiting for ack of '${message.javaClass.simpleName}'") { ack ->
            ack.acknowledgedMessageId == message.messageId
        }
    }

    public suspend fun awaitReload(): Unit = withAsyncTrace("'awaitReload'") {
        val reloadRequest = skipToMessage<ReloadClassesRequest>()
        val reloadResult = skipToMessage<ReloadClassesResult>()
        if (!reloadResult.isSuccess) {
            fail("Failed to reload classes: ${reloadResult.errorMessage}", Throwable(reloadResult.errorMessage).apply {
                stackTrace = reloadResult.errorStacktrace?.toTypedArray().orEmpty()
            })
        }

        val rendered = skipToMessage<UIRendered>()
        assertEquals(reloadRequest.messageId, rendered.reloadRequestId)
        sync()
    }

    public suspend infix fun initialSourceCode(source: String): Path = withAsyncTrace("'initialSourceCode'") run@{
        val file = writeCode(source = source)
        launchApplicationAndWait()
        sync()
        return@run file
    }

    public suspend fun replaceSourceCodeAndReload(
        sourceFile: String = fixture.getDefaultMainKtSourceFile(),
        oldValue: String, newValue: String
    ): Unit = withAsyncTrace("'replaceSourceCodeAndReload'") run@{
        replaceSourceCode(sourceFile, oldValue, newValue)
        awaitReload()
    }

    public fun replaceSourceCode(
        sourceFile: String,
        oldValue: String, newValue: String
    ) {
        val resolvedFile = fixture.projectDir.resolve(sourceFile)
        val previousText = resolvedFile.readText()
        val updatedText = previousText.replace(oldValue, newValue)
        if (updatedText == previousText) {
            error("Replacement '$oldValue' -> '$newValue' not recognized did not change source code. Typo?")
        }
        writeCode(sourceFile, updatedText)
    }

    public fun replaceSourceCode(oldValue: String, newValue: String): Unit =
        replaceSourceCode(fixture.getDefaultMainKtSourceFile(), oldValue, newValue)


    public fun writeCode(
        sourceFile: String = fixture.getDefaultMainKtSourceFile(),
        @Language("kotlin") source: String
    ): Path {
        val resolvedFile = fixture.projectDir.resolve(sourceFile)
        resolvedFile.createParentDirectories()
        resolvedFile.writeText(source)
        return resolvedFile
    }
}
