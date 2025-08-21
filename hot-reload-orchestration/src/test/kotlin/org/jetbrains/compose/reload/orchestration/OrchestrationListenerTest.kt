/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.orchestration

import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.reload.core.await
import org.jetbrains.compose.reload.core.awaitIdle
import org.jetbrains.compose.reload.core.awaitOrThrow
import org.jetbrains.compose.reload.core.getOrThrow
import org.jetbrains.compose.reload.core.isActive
import org.jetbrains.compose.reload.core.isStopped
import org.jetbrains.compose.reload.core.reloadMainThread
import org.jetbrains.compose.reload.orchestration.utils.await
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import kotlin.test.Test
import kotlin.test.assertEquals

class OrchestrationListenerTest {

    @Test
    fun `test - simple connect`() = runTest {
        val awaitingClient = startOrchestrationListener(OrchestrationClientRole.Unknown)

        val server = startOrchestrationServer()
        server.connectClient(awaitingClient.port.awaitOrThrow())

        val client = await("Client Connection") {
            awaitingClient.connections.receive().getOrThrow()
        }

        await("Server: Client State") {
            server.states.get(OrchestrationConnectionsState).await { state ->
                setOf(client.clientId) == state.connections.map { it.clientId }.toSet()
            }
        }

        await("Client: Client State") {
            server.states.get(OrchestrationConnectionsState).await { state ->
                setOf(client.clientId) == state.connections.map { it.clientId }.toSet()
            }
        }
    }

    @Test
    fun `test - connect and disconnect`() = runTest {
        val server = startOrchestrationServer()
        val deferred = startOrchestrationListener(OrchestrationClientRole.Unknown)
        server.connectClient(deferred.port.awaitOrThrow())
        val client = deferred.connections.receive().getOrThrow()

        await("Server: Client State") {
            server.states.get(OrchestrationConnectionsState).await {
                setOf(client.clientId) == it.connections.map { it.clientId }.toSet()
            }
        }

        client.close()
        await("Server: Client State") {
            server.states.get(OrchestrationConnectionsState).await {
                it.connections.isEmpty()
            }
        }
    }

    @Test
    fun `test - connect and server close`() = runTest {
        val server = startOrchestrationServer()
        val deferred = startOrchestrationListener(OrchestrationClientRole.Unknown)
        server.connectClient(deferred.port.awaitOrThrow())
        val client = deferred.connections.receive().getOrThrow()

        await("Server: Client State") {
            server.states.get(OrchestrationConnectionsState).await {
                setOf(client.clientId) == it.connections.map { it.clientId }.toSet()
            }
        }

        server.close()
        await("Client Closed") { client.await() }

        assertEquals(true, client.isCompleted())
        assertEquals(false, client.isActive())
    }

    @Test
    fun `test - connect and listener close`() = runTest {
        val server = startOrchestrationServer()
        val listener = startOrchestrationListener(OrchestrationClientRole.Unknown)
        server.connectClient(listener.port.awaitOrThrow())
        val client = listener.connections.receive().getOrThrow()

        assertTrue(client.isActive())
        listener.close()
        reloadMainThread.awaitIdle()

        assertFalse(client.isStopped(), "Expected client to be stopped when listener is closed")
        await("Client Closed") { client.await() }
    }
}
