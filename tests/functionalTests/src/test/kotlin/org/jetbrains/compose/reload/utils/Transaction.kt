package org.jetbrains.compose.reload.utils

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
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.RecompilerReady
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ReloadClassesRequest
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ReloadClassesResult
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.UIRendered
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
annotation class TransactionDslMarker

@TransactionDslMarker
class TransactionScope(
    val fixture: HotReloadTestFixture,
    private val coroutineScope: CoroutineScope,
    val incomingMessages: ReceiveChannel<OrchestrationMessage>,
) : CoroutineScope by coroutineScope {

    val logger = createLogger()

    fun OrchestrationMessage.send() {
        fixture.orchestration.sendMessage(this).get()
    }

    suspend fun launchChildTransaction(block: suspend TransactionScope.() -> Unit): Job {
        return launch(AsyncTraces("launchChildTransaction")) {
            TransactionScope(fixture, this@launch, fixture.createReceiveChannel()).block()
        }
    }

    suspend inline fun <reified T> skipToMessage(
        title: String = T::class.simpleName.toString(),
        timeout: Duration = 5.minutes,
        crossinline filter: (T) -> Boolean = { true }
    ): T = withAsyncTrace("'skipToMessage($title)'") {
        val dispatcher = Dispatchers.Default.limitedParallelism(1)
        val asyncTracesString = asyncTracesString()

        val reminder = fixture.daemonTestScope.launch(dispatcher) {
            val sleep = 5.seconds
            var waiting = 0.seconds
            while (true) {
                logger.info(
                    "Waiting for message $title ($waiting/$timeout)\n" +
                        asyncTracesString.prependIndent("\t")
                )
                delay(sleep)
                waiting += sleep
            }
        }

        withContext(dispatcher) {
            try {
                withTimeout(if (fixture.isDebug) 24.hours else timeout) {
                    incomingMessages.receiveAsFlow().filterIsInstance<T>().filter(filter).first()
                }
            } finally {
                reminder.cancel()
            }
        }
    }

    suspend fun launchApplicationAndWait(
        projectPath: String = ":",
        mainClass: String = "MainKt",
    ) = withAsyncTrace("'launchApplicationAndWait'") {
        fixture.launchApplication(projectPath, mainClass)
        var uiRendered = false
        var recompilerReady = false

        skipToMessage<OrchestrationMessage>("Waiting for application to start") { message ->
            if (message is UIRendered) uiRendered = true
            if (message is RecompilerReady) recompilerReady = true
            uiRendered && recompilerReady
        }
    }

    suspend fun launchDevApplicationAndWait(
        projectPath: String = ":",
        className: String,
        funName: String
    ) = withAsyncTrace("'launchDevApplicationAndWait'") {
        fixture.launchDevApplication(projectPath, className, funName)
        var uiRendered = false
        var recompilerReady = false

        skipToMessage<OrchestrationMessage>("Waiting for dev application to start") { message ->
            if (message is UIRendered) uiRendered = true
            if (message is RecompilerReady) recompilerReady = true
            uiRendered && recompilerReady
        }
    }

    suspend fun sync() = withAsyncTrace("'sync'") {
        val ping = OrchestrationMessage.Ping()
        ping.send()
        awaitAck(ping)
    }

    suspend fun awaitAck(message: OrchestrationMessage) = withAsyncTrace("'awaitAck'") run@{
        /*
        There is no ACK for an ACK, and there is also no ACk for a shutdown request.
         */
        if (message is OrchestrationMessage.Ack || message is OrchestrationMessage.ShutdownRequest) return@run
        skipToMessage<OrchestrationMessage.Ack>("Waiting for ack of '${message.javaClass.simpleName}'") { ack ->
            ack.acknowledgedMessageId == message.messageId
        }
    }

    suspend fun awaitReload() = withAsyncTrace("'awaitReload'") {
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

    suspend infix fun initialSourceCode(source: String): Path = withAsyncTrace("'initialSourceCode'") run@{
        val file = writeCode(source = source)
        launchApplicationAndWait()
        sync()
        return@run file
    }

    suspend fun replaceSourceCodeAndReload(
        sourceFile: String = fixture.getDefaultMainKtSourceFile(),
        oldValue: String, newValue: String
    ) = withAsyncTrace("'replaceSourceCodeAndReload'") run@ {
        replaceSourceCode(sourceFile, oldValue, newValue)
        awaitReload()
    }

    fun replaceSourceCode(
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

    fun replaceSourceCode(oldValue: String, newValue: String) =
        replaceSourceCode(fixture.getDefaultMainKtSourceFile(), oldValue, newValue)


    fun writeCode(
        sourceFile: String = fixture.getDefaultMainKtSourceFile(),
        @Language("kotlin") source: String
    ): Path {
        val resolvedFile = fixture.projectDir.resolve(sourceFile)
        resolvedFile.createParentDirectories()
        resolvedFile.writeText(source)
        return resolvedFile
    }
}
