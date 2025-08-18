/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.devtools.tests.state

import io.sellmair.evas.Events
import io.sellmair.evas.States
import io.sellmair.evas.set
import io.sellmair.evas.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.jetbrains.compose.devtools.states.ReloadUIState
import org.jetbrains.compose.devtools.states.launchReloadStateActor
import org.jetbrains.compose.devtools.states.launchReloadUIState
import org.jetbrains.compose.reload.core.Future
import org.jetbrains.compose.reload.core.awaitIdle
import org.jetbrains.compose.reload.core.complete
import org.jetbrains.compose.reload.core.invokeOnValue
import org.jetbrains.compose.reload.core.reloadMainThread
import org.jetbrains.compose.reload.orchestration.OrchestrationHandle
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ReloadClassesRequest
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ReloadClassesResult
import org.jetbrains.compose.reload.orchestration.startOrchestrationServer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.seconds

class ReloadStateTest {

    @Test
    fun `test - build started - build finished`() = runTest(States() + Events()) {
        val orchestration = startOrchestrationServer()

        currentCoroutineContext().job.invokeOnCompletion { orchestration.close() }
        launchReloadStateActor(orchestration)
        launchReloadUIState(orchestration)
        testScheduler.advanceUntilIdle()

        assertIs<ReloadUIState.Ok>(ReloadUIState.value())
        orchestration.sendAndWait(OrchestrationMessage.BuildStarted())
        reloadMainThread.awaitIdle()
        testScheduler.advanceUntilIdle()
        assertIs<ReloadUIState.Reloading>(ReloadUIState.value())


        orchestration.sendAndWait(OrchestrationMessage.BuildFinished())
        reloadMainThread.awaitIdle()
        testScheduler.advanceUntilIdle()
        assertIs<ReloadUIState.Ok>(ReloadUIState.value())

        orchestration.stop()
        currentCoroutineContext().job.cancelChildren()
    }

    @Test
    fun `test - build started  - reload request - build finished - reload result`() = runTest(States() + Events()) {
        val orchestration = startOrchestrationServer()
        currentCoroutineContext().job.invokeOnCompletion { orchestration.close() }
        launchReloadStateActor(orchestration)
        launchReloadUIState(orchestration)
        testScheduler.advanceUntilIdle()

        assertIs<ReloadUIState.Ok>(ReloadUIState.value())

        /* Send 'Build Started -> Expect 'Reloading*' */
        orchestration.sendAndWait(OrchestrationMessage.BuildStarted())
        reloadMainThread.awaitIdle()
        testScheduler.advanceUntilIdle()
        val initialReloading = assertIs<ReloadUIState.Reloading>(ReloadUIState.value())

        /* Send 'Reload Request' -> Expect 'Reloading' with retained request */
        val reloadRequest = ReloadClassesRequest()
        orchestration.send(reloadRequest)

        reloadMainThread.awaitIdle()
        testScheduler.advanceUntilIdle()
        val reloadingState = assertIs<ReloadUIState.Reloading>(ReloadUIState.value())
        assertEquals(reloadRequest.messageId, reloadingState.reloadRequestId)
        assertEquals(initialReloading.time, reloadingState.time)

        /* Send 'Build Finished' -> Still expect 'Reloading' as we're waiting for the 'Reload Result' now */
        orchestration sendAndWait OrchestrationMessage.BuildFinished()
        reloadMainThread.awaitIdle()
        testScheduler.advanceUntilIdle()
        assertEquals(reloadRequest.messageId, assertIs<ReloadUIState.Reloading>(ReloadUIState.value()).reloadRequestId)

        /* Send 'ReloadClassesResult' -> Expect 'Ok' */
        orchestration sendAndWait ReloadClassesResult(
            reloadRequestId = reloadRequest.messageId,
            isSuccess = true,
        )
        reloadMainThread.awaitIdle()
        testScheduler.advanceUntilIdle()
        assertIs<ReloadUIState.Ok>(ReloadUIState.value())

        currentCoroutineContext().job.cancelChildren()
    }

    @Test
    fun `test - failed reload result`() = runTest(States() + Events()) {
        val orchestration = startOrchestrationServer()
        currentCoroutineContext().job.invokeOnCompletion { orchestration.close() }
        launchReloadStateActor(orchestration)
        launchReloadUIState(orchestration)
        testScheduler.advanceUntilIdle()

        val request = ReloadClassesRequest()
        ReloadUIState.set(ReloadUIState.Reloading(reloadRequestId = request.messageId))

        /* Send failed 'Reload Result' -> Expect 'Failed' */
        orchestration sendAndWait ReloadClassesResult(
            reloadRequestId = request.messageId, isSuccess = false,
        )
        reloadMainThread.awaitIdle()
        testScheduler.advanceUntilIdle()
        assertIs<ReloadUIState.Failed>(ReloadUIState.value())

        currentCoroutineContext().job.cancelChildren()
    }

    @Test
    fun `test - build task failure`() = runTest(States() + Events()) {
        val orchestration = startOrchestrationServer()
        currentCoroutineContext().job.invokeOnCompletion { orchestration.close() }
        launchReloadStateActor(orchestration)
        launchReloadUIState(orchestration)
        testScheduler.advanceUntilIdle()

        ReloadUIState.set(ReloadUIState.Reloading())

        orchestration sendAndWait OrchestrationMessage.BuildTaskResult(
            taskId = ":test", isSuccess = false, isSkipped = false,
            startTime = null, endTime = null, failures = emptyList()
        )

        reloadMainThread.awaitIdle()
        testScheduler.advanceUntilIdle()
        assertIs<ReloadUIState.Failed>(ReloadUIState.value())

        currentCoroutineContext().job.cancelChildren()
    }
}

internal suspend infix fun OrchestrationHandle.sendAndWait(message: OrchestrationMessage) {
    val future = Future()
    val disposable = messages.invokeOnValue { incoming ->
        if (incoming.messageId == message.messageId) {
            future.complete()
        }
    }
    try {
        reloadMainThread.awaitIdle()
        send(message)
        future.await()
    } finally {
        disposable.dispose()
    }
}
