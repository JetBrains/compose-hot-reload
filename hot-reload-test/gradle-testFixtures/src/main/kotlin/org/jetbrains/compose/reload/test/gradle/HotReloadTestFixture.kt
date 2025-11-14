/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

@file:OptIn(ExperimentalPathApi::class)

package org.jetbrains.compose.reload.test.gradle

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import org.jetbrains.compose.reload.core.Environment
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.error
import org.jetbrains.compose.reload.core.withAsyncTrace
import org.jetbrains.compose.reload.orchestration.LogMessage
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ShutdownRequest
import org.jetbrains.compose.reload.orchestration.OrchestrationServer
import org.jetbrains.compose.reload.orchestration.asChannel
import org.jetbrains.compose.reload.orchestration.asFlow
import org.jetbrains.compose.reload.orchestration.sendAsync
import org.jetbrains.compose.reload.test.core.AppClasspath
import org.jetbrains.kotlin.tooling.core.Extras
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes


@TransactionDslMarker
public class HotReloadTestFixture
internal constructor(
    public val testClassName: String,
    public val testMethodName: String,
    public val projectDir: ProjectDir,
    public val gradleRunner: GradleRunner,
    public val orchestration: OrchestrationServer,
    public val projectMode: ProjectMode,
    public val launchMode: ApplicationLaunchMode,
    public val buildMode: BuildMode,
    @PublishedApi
    internal val isDebug: Boolean,
    internal val kotlinVersion: TestedKotlinVersion,
    internal val composeVersion: TestedComposeVersion,
    internal val gradleVersion: TestedGradleVersion,
    internal val extras: Extras
) : AutoCloseable {

    private val logger = createLogger("Test Fixture", dispatch = listOf(orchestration.startLoggerDispatch()))

    public suspend fun <T> runTransaction(
        block: suspend TransactionScope.() -> T
    ): T = withAsyncTrace("'runTransaction'") {
        coroutineScope {
            /*
            Multiple consumers will be able to 'receive' all messages sent during the transaction.
            Therefore, this shared flow is buffered with unlimited replay.
             */
            val sharedMessages = orchestration.asFlow()
                .buffer(Channel.UNLIMITED)
                .shareIn(this, SharingStarted.Eagerly, replay = Channel.UNLIMITED)

            /*
            This is the transactions' message channel.
            This can be used to linearly progress through the transaction.
             */
            val messageChannel = Channel<OrchestrationMessage>(Channel.UNLIMITED)
            launch { sharedMessages.collect { message -> messageChannel.send(message) } }

            try {
                coroutineScope {
                    val scope = TransactionScope(
                        fixture = this@HotReloadTestFixture,
                        coroutineScope = this,
                        sharedMessages = sharedMessages,
                        orchestration.asChannel()
                    )
                    scope.block()
                }
            } finally {
                currentCoroutineContext().cancelChildren()
                messageChannel.close()
            }
        }
    }

    public suspend fun <T> sendMessage(
        message: OrchestrationMessage,
        transaction: suspend TransactionScope.() -> T
    ): T {
        return runTransaction {
            message.send()
            transaction()
        }
    }

    public fun <T> launchTestDaemon(
        context: CoroutineContext = EmptyCoroutineContext,
        daemon: suspend CoroutineScope.() -> T
    ): Deferred<T> {
        return daemonTestScope.async(context) { daemon() }
    }

    internal lateinit var testScope: TestScope
        private set

    /**
     * Coroutines launched in this scope will not keep the 'runTest' blocking.
     * This scope will be canceled after the 'runTest' finished (e.g., useful for launching 'Daemon Coroutines)
     */
    @PublishedApi
    internal lateinit var daemonTestScope: CoroutineScope

    public fun runTest(timeout: Duration = 15.minutes, test: suspend HotReloadTestFixture.() -> Unit) {
        kotlinx.coroutines.test.runTest(timeout = if (isDebug) 24.hours else timeout) {
            testScope = this
            daemonTestScope = CoroutineScope(currentCoroutineContext() + Job(currentCoroutineContext().job))

            /*
            Forward critical exceptions from the connected components to this test.
             */
            daemonTestScope.launch {
                orchestration.asFlow().filterIsInstance<OrchestrationMessage.CriticalException>()
                    .collect { disconnected ->
                        val exception = CriticalExceptionCancellation(disconnected)
                        logger.error("CriticalException: '${disconnected.message}'", exception)
                        testScope.cancel(exception)
                    }
            }

            /*
            Check Application classpath for suspicious content (such as compose dependencies not matching
            the expected version from the test fixture
             */
            daemonTestScope.launch {
                orchestration.asFlow().filterIsInstance<OrchestrationMessage.TestEvent>().collect { event ->
                    val classpath = event.payload as? AppClasspath ?: return@collect
                    checkClasspath(classpath, composeVersion)
                }
            }

            /* Forward all build outputs */
            daemonTestScope.launch {
                val stderr = gradleRunner.stderrChannel?.receiveAsFlow() ?: emptyFlow()
                val stdout = gradleRunner.stdoutChannel?.receiveAsFlow() ?: emptyFlow()
                merge(stderr, stdout).collect { message ->
                    orchestration sendAsync LogMessage(
                        environment = Environment.build,
                        loggerName = "Gradle Test Runner",
                        message = message
                    )
                }
            }

            try {
                test()
            } finally {
                daemonTestScope.cancel()
                daemonTestScope.coroutineContext[Job]?.join()
            }
        }
    }

    private val resourcesLock = ReentrantLock()
    private val resources = mutableListOf<AutoCloseable>()

    override fun close() {
        runBlocking {
            orchestration.send(ShutdownRequest("Requested by HotReloadTestFixture.close()"))
            orchestration.close()
        }

        testScope.cancel()
        daemonTestScope.cancel()

        /* Use multiple attempts to delete the projectDir */
        var cleanupAttempts = 0
        while (true) {
            val result = runCatching { projectDir.path.deleteRecursively() }
            if (result.isSuccess) break
            cleanupAttempts++
            if (cleanupAttempts > 10) {
                logger.error("Failed cleaning up projectDir: ${projectDir.path}")
                break
            }
            Thread.sleep(128)
        }

        resourcesLock.withLock {
            resources.forEach { resource -> resource.close() }
            resources.clear()
        }
    }
}

private class CriticalExceptionCancellation(
    criticalExceptionMessage: OrchestrationMessage.CriticalException
) : CancellationException("${criticalExceptionMessage.exceptionClassName}: ${criticalExceptionMessage.message}") {
    init {
        stackTrace = criticalExceptionMessage.stacktrace.toTypedArray()
    }
}
