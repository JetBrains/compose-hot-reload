/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.test

import androidx.compose.runtime.Composable
import org.jetbrains.compose.reload.agent.send
import org.jetbrains.compose.reload.jvm.runHeadlessApplicationBlocking
import org.jetbrains.compose.reload.orchestration.OrchestrationClientRole
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles
import kotlin.time.Duration.Companion.minutes

internal val logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())


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

        OrchestrationMessage.CriticalException(
            clientRole = OrchestrationClientRole.Application,
            message = throwable.message,
            exceptionClassName = throwable.javaClass.name,
            stacktrace = thread.stackTrace.toList()
        ).send()
    }

    runHeadlessApplicationBlocking(
        timeout.minutes, width = width, height = height, content = {
            content()
        }
    )
}
