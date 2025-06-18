/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.test.gradle

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.intellij.lang.annotations.Language
import org.jetbrains.compose.reload.core.AsyncTraces
import org.jetbrains.compose.reload.core.Logger
import org.jetbrains.compose.reload.core.asyncTracesString
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.error
import org.jetbrains.compose.reload.core.warn
import org.jetbrains.compose.reload.core.withAsyncTrace
import org.jetbrains.compose.reload.orchestration.OrchestrationClientRole.Application
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.Ack
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ClientDisconnected
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.Ping
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ReloadClassesRequest
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ReloadClassesResult
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.UIRendered
import org.jetbrains.compose.reload.orchestration.asFlow
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

    /**
     * A shared flow of all messages during the transaction.
     * This flow is supposed to replay all messages for new subscribers.
     */
    public val sharedMessages: SharedFlow<OrchestrationMessage>,

    /**
     * Single channel representing the state of the transaction.
     * This 'linear' channel can be used in [pullMessages] or [skipToMessage]]
     */
    @PublishedApi
    internal val message: ReceiveChannel<OrchestrationMessage>
) : CoroutineScope by coroutineScope {

    internal val daemonScope = CoroutineScope(coroutineScope.coroutineContext + Job()).also { daemon ->
        coroutineScope.coroutineContext.job.invokeOnCompletion {
            daemon.cancel()
        }
    }

    @PublishedApi
    internal val logger: Logger = createLogger()

    public suspend fun OrchestrationMessage.send() {
        fixture.orchestration.send(this)
    }

    /**
     * Launches a coroutine, which will not 'block' the current transaction.
     * Such daemons can be used to setup hooks such as 'fail the test if a certain message XYZ is received
     * during the transaction.'
     */
    public fun launchDaemon(block: suspend () -> Unit): Job = daemonScope.launch {
        block()
    }

    /**
     * Launches the [block] as a child transaction.
     * The child transaction will replay all messages received prior.
     * The current transaction will have to wait for this child transaction to complete as well.
     */
    public suspend fun launchChildTransaction(block: suspend TransactionScope.() -> Unit): Job {
        val currentMessage = Channel<OrchestrationMessage>(Channel.UNLIMITED)
        val currentMessageCollector = launch {
            sharedMessages.collect { message -> currentMessage.send(message) }
        }

        val job = launch(AsyncTraces("launchChildTransaction")) {
            TransactionScope(fixture, this, sharedMessages, currentMessage).block()
        }

        job.invokeOnCompletion {
            currentMessageCollector.cancel()
            currentMessage.close()
        }

        return job

    }

    /**
     * Collects all currently available messages into a list
     */
    public fun pullMessages(): List<OrchestrationMessage> = buildList {
        while (true) {
            message.tryReceive().getOrNull()?.let(::add) ?: break
        }
    }

    /**
     * Skips this [TransactionScope] until the suitable message is received.
     */
    public suspend inline fun <reified T : OrchestrationMessage> skipToMessage(
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
                delay(sleep)
                logger.warn(
                    "'$title' ($waiting/$timeout)\n" +
                        asyncTracesString.prependIndent("\t")
                )
                waiting += sleep
            }
        }

        /* Monitor the application (might crash and disconnect) */
        val applicationMonitor = launch {
            // No need to monitor if the skip is actually skipping to this message anyway
            if (T::class == ClientDisconnected::class) return@launch
            fixture.orchestration.asFlow().filterIsInstance<ClientDisconnected>().collect { message ->
                if (message.clientRole == Application) {
                    logger.error("'$title': Application disconnected.")
                    fail("Application disconnected. $asyncTracesString")
                }
            }
        }

        withContext(dispatcher) {
            try {
                withTimeout(if (fixture.isDebug) 24.hours else timeout) {
                    message.receiveAsFlow().filterIsInstance<T>().filter(filter).first()
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
        awaitApplicationStart()
    }

    public suspend fun launchDevApplicationAndWait(
        projectPath: String = ":",
        className: String,
        funName: String
    ): Unit = withAsyncTrace("'launchDevApplicationAndWait'") {
        fixture.launchDevApplication(projectPath, className, funName)
        awaitApplicationStart()
    }

    public suspend fun awaitApplicationStart(): Unit = withAsyncTrace("'awaitApplicationStart'") {
        /* That would be a bummer; Let's fail on such a disconnect */
        launchDaemon {
            sharedMessages.filterIsInstance<ClientDisconnected>().filter { it.clientRole == Application }.collect {
                fail("Application disconnected")
            }
        }

        coroutineScope {
            /* If the build mode is Continuous, we want to await the recompiler to be ready */
            if (fixture.buildMode == BuildMode.Continuous) {
                launchChildTransaction {
                    /* Ready is signaled by an empty request (UP TO DATE) */
                    skipToMessage<ReloadClassesRequest>("Waiting for recompiler to be ready") { request ->
                        request.changedClassFiles.isEmpty()
                    }
                }
            }

            skipToMessage<UIRendered>("Waiting for application to start")
        }
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
        skipToMessage<Ack>("Waiting for ack of '${message.messageId}'") { ack ->
            ack.acknowledgedMessageId == message.messageId
        }
    }

    public suspend fun requestAndAwaitReload(): Unit = withAsyncTrace("'requestAndAwaitReload'") {
        requestReload()
        awaitReload()
    }

    public suspend fun requestReload(): Iterable<GradleBuildEvent> = withAsyncTrace("'reload'") {
        fixture.gradleRunner.buildFlow("reload").toList().assertSuccessful()
    }

    public suspend fun awaitReload(): Unit = withAsyncTrace("'awaitReload'") {
        val reloadRequest = skipToMessage<ReloadClassesRequest>()
        val reloadResult = skipToMessage<ReloadClassesResult> { result ->
            result.reloadRequestId == reloadRequest.messageId
        }
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
        when (fixture.buildMode) {
            BuildMode.Explicit -> fixture.gradleRunner.build("reload").assertSuccess()
            else -> Unit
        }
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
