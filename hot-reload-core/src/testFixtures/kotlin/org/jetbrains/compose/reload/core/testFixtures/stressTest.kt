/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.core.testFixtures

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.jetbrains.compose.reload.core.createLogger
import org.junit.jupiter.api.fail
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private val logger = createLogger()

class StressTestScope(
    val invocationIndex: Int,
    override val coroutineContext: CoroutineContext,
    private val reportActivityChannel: SendChannel<String>,
) : CoroutineScope {

    private val resources = mutableListOf<AutoCloseable>()
    private val lock = ReentrantLock()

    suspend fun <T : AutoCloseable> use(value: T): T {
        currentCoroutineContext().job.invokeOnCompletion { value.close() }
        lock.withLock {
            resources.add(value)
            return value
        }
    }

    init {
        coroutineContext.job.invokeOnCompletion {
            lock.withLock {
                resources.forEach { it.close() }
            }
        }
    }

    suspend fun reportActivity(message: String) {
        ensureActive()
        reportActivityChannel.send(message)
        yield()
    }
}

fun runStressTest(
    repetitions: Int = 24,
    parallelism: Int = 2,
    timeout: Duration = 10.minutes,
    silenceTimeout: Duration = 30.seconds,
    test: suspend StressTestScope.() -> Unit
) {
    runBlocking(Dispatchers.IO + Job() + CoroutineName("runStressTest")) {
        /* Fan out the invocations using a channel */
        val invocationChannel = Channel<Int>(Channel.UNLIMITED)
        repeat(repetitions) { index -> invocationChannel.send(index) }
        invocationChannel.close()

        val executedInvocations = AtomicInteger(0)

        /* Setup tests overall timeout */
        withTimeout(timeout) {
            coroutineScope {
                /* Launch coroutines */
                repeat(parallelism) { coroutineId ->
                    launch(CoroutineName("runStressTest(coroutineId=$coroutineId)")) {
                        for (invocationIndex in invocationChannel) {
                            executeStressTestInvocation(coroutineId, invocationIndex, silenceTimeout, test)
                            val executedInvocations = executedInvocations.incrementAndGet()
                            logger.info("runStressTest: $executedInvocations/$repetitions")
                        }
                    }
                }
            }
        }
    }
}


@OptIn(ExperimentalCoroutinesApi::class)
private suspend fun CoroutineScope.executeStressTestInvocation(
    coroutineId: Int, invocationIndex: Int,
    silenceTimeout: Duration,
    test: suspend StressTestScope.() -> Unit
) {
    val reportActivityChannel = Channel<String>(Channel.UNLIMITED)

    val silenceDetector = launch(CoroutineName("Silence Detector #$coroutineId")) {
        val reports = mutableListOf<String>()

        while (isActive) {
            val report = select {
                reportActivityChannel.onReceiveCatching { it }
                onTimeout(silenceTimeout) { null }
            }

            if (report == null) {
                logger.error("Stress test #$invocationIndex timed out (coroutine: $coroutineId)")
                fail(
                    "Silence Timeout at coroutine '$coroutineId', invocation '$invocationIndex'\n" +
                        reports.takeLast(64).joinToString("\n")
                )
            }

            reports.add(report.getOrNull() ?: break)
        }
    }

    try {
        coroutineScope {
            withContext(CoroutineName("stressTestScope.test($invocationIndex)")) {
                val stressTestScope = StressTestScope(invocationIndex, coroutineContext, reportActivityChannel)
                stressTestScope.test()
            }
        }
        silenceDetector.cancel()
    } catch (t: Throwable) {
        ensureActive()
        throw AssertionError(
            "Error at coroutine '$coroutineId', invocation '$invocationIndex'", t
        )
    } finally {
        reportActivityChannel.close()
    }
}
