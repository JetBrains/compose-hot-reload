/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.devtools.tests.state

import io.sellmair.evas.Events
import io.sellmair.evas.States
import io.sellmair.evas.value
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.devtools.states.BuildSystemState
import org.jetbrains.compose.devtools.states.launchBuildSystemState
import org.jetbrains.compose.reload.core.BuildSystem
import org.jetbrains.compose.reload.core.Logger
import org.jetbrains.compose.reload.core.awaitIdle
import org.jetbrains.compose.reload.core.reloadMainThread
import org.jetbrains.compose.reload.orchestration.OrchestrationHandle
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import org.jetbrains.compose.reload.orchestration.startOrchestrationServer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class BuildSystemStateTest {
    @Test
    fun `test - build system initialised`() = runTest(States() + Events()) {
        val orchestration = startOrchestrationServer()

        currentCoroutineContext().job.invokeOnCompletion { orchestration.close() }
        launchBuildSystemState(orchestration)
        testScheduler.advanceUntilIdle()

        assertIs<BuildSystemState.Unknown>(BuildSystemState.value())

        orchestration.setLogAndAwait(message = "Recompiler created: '${BuildSystem.Gradle}'")
        reloadMainThread.awaitIdle()
        testScheduler.advanceUntilIdle()
        val value = BuildSystemState.value()
        assertIs<BuildSystemState.Initialised>(value)
        assertEquals(BuildSystem.Gradle, value.buildSystem)

        currentCoroutineContext().job.cancelChildren()
    }
}


private suspend fun OrchestrationHandle.setLogAndAwait(
    level: Logger.Level = Logger.Level.Debug,
    message: String = "",
) {
    sendAndWait(
        OrchestrationMessage.LogMessage(
            environment = null,
            loggerName = "<<TEST>>",
            threadName = "<<UNKNOWN>>",
            timestamp = System.currentTimeMillis(),
            level = level,
            message = message,
            throwableClassName = null,
            throwableMessage = null,
            throwableStacktrace = null
        )
    )
}
