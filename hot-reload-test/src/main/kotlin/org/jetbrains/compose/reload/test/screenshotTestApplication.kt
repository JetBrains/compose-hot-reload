/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.test

import androidx.compose.runtime.Composable
import org.jetbrains.compose.reload.agent.sendAsync
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.error
import org.jetbrains.compose.reload.core.info
import org.jetbrains.compose.reload.jvm.runHeadlessApplicationBlocking
import org.jetbrains.compose.reload.orchestration.OrchestrationClientRole
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.TestEvent
import org.jetbrains.compose.reload.test.core.AppClasspath
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private val logger = createLogger()

/**
 * Entry points for "Applications under test"
 */
@Suppress("unused") // Used by integration tests
public fun screenshotTestApplication(
    timeout: Int = 5,
    width: Int = 512,
    height: Int = 512,
    content: @Composable () -> Unit
) {
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        logger.error("Uncaught exception in thread: $thread", throwable)

        try {
            OrchestrationMessage.CriticalException(
                clientRole = OrchestrationClientRole.Application,
                message = throwable.message,
                exceptionClassName = throwable.javaClass.name,
                stacktrace = throwable.stackTrace.toList()
            ).sendAsync()
        } catch (t: Throwable) {
            logger.error("Failed to send critical exception", t)
        } finally {
            logger.info("Sent critical exception")
        }
    }

    TestEvent(AppClasspath.current).sendAsync()

    runHeadlessApplicationBlocking(
        timeout.minutes, silenceTimeout = 30.seconds, width = width, height = height, content = {
            content()
        }
    )
}
