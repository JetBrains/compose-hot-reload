/*
 * Copyright 2024-2026 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.orchestration

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.jetbrains.compose.reload.core.getOrThrow
import org.jetbrains.compose.reload.core.invokeOnValue
import org.jetbrains.compose.reload.core.launchTask
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ReloadClassesRequest
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ReloadClassesResult
import org.junit.jupiter.api.AfterEach
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class SendAndAwaitTest {
    private val resources = mutableListOf<OrchestrationHandle>()

    @AfterEach
    fun cleanup() = resources.forEach { it.close() }

    /** Starts a server; closed by [cleanup]. */
    private fun server(): OrchestrationServer = startOrchestrationServer().also { resources.add(it) }

    /** Connects a client to [server]; closed by [cleanup]. */
    private suspend fun OrchestrationServer.client(): OrchestrationClient =
        OrchestrationClient(OrchestrationClientRole.Unknown, port.await().getOrThrow()).also {
            it.connect().getOrThrow()
            resources.add(it)
        }

    /**
     * Answers every incoming [ReloadClassesRequest] with [isSuccess], preceded by a result of an
     * unrelated request: the awaited matchers must skip that stale result.
     */
    private fun OrchestrationHandle.replyToReloadRequests(isSuccess: Boolean) {
        val handle = this
        messages.invokeOnValue { message ->
            if (message is ReloadClassesRequest) {
                launchTask("replyToReloadRequests") {
                    handle.send(ReloadClassesResult(ReloadClassesRequest().messageId, isSuccess = false))
                    handle.send(ReloadClassesResult(message.messageId, isSuccess))
                }
            }
        }
    }

    @Test
    fun `test - sendAndAwait returns the matching reply`() = runTest(timeout = 10.seconds) {
        val server = server()
        val responder = server.client()
        val requester = server.client()
        responder.replyToReloadRequests(isSuccess = true)

        /* Real dispatcher: under runTest's virtual clock the timeout would fire before a real reply arrives */
        val request = ReloadClassesRequest()
        val reply = withContext(Dispatchers.Default) {
            requester.sendAndAwait(request, 10.seconds) { message ->
                (message as? ReloadClassesResult)?.takeIf { it.reloadRequestId == request.messageId }
            }
        }

        assertTrue(reply?.isSuccess == true, "Expected the matching ReloadClassesResult, got $reply")
    }

    @Test
    fun `test - sendAndAwait yields null on timeout`() = runTest(timeout = 10.seconds) {
        val server = server()
        val requester = server.client()

        val reply = withContext(Dispatchers.Default) {
            requester.sendAndAwait(ReloadClassesRequest(), 500.milliseconds) { it as? ReloadClassesResult }
        }

        assertNull(reply, "Expected no reply without a responder")
    }

    @Test
    fun `test - sendAndAwaitBlocking returns the matching reply`() = runTest(timeout = 10.seconds) {
        val server = server()
        val responder = server.client()
        val requester = server.client()
        responder.replyToReloadRequests(isSuccess = true)

        val request = ReloadClassesRequest()
        val reply = requester.sendAndAwaitBlocking(request, 10.seconds) { message ->
            (message as? ReloadClassesResult)?.takeIf { it.reloadRequestId == request.messageId }
        }

        assertTrue(reply?.isSuccess == true, "Expected the matching ReloadClassesResult, got $reply")
    }

    @Test
    fun `test - sendAndAwaitBlocking yields null on timeout`() = runTest(timeout = 10.seconds) {
        val server = server()
        val requester = server.client()

        val reply = requester.sendAndAwaitBlocking(ReloadClassesRequest(), 500.milliseconds) {
            it as? ReloadClassesResult
        }

        assertNull(reply, "Expected no reply without a responder")
    }
}
