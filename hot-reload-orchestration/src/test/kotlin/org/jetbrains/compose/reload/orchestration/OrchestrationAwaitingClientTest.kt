/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.orchestration

import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.reload.core.await
import org.jetbrains.compose.reload.core.awaitOrThrow
import org.jetbrains.compose.reload.core.isActive
import org.jetbrains.compose.reload.orchestration.utils.await
import kotlin.test.Test
import kotlin.test.assertEquals

class OrchestrationAwaitingClientTest {

    @Test
    fun `test - simple connect`() = runTest {
        val deferred = startOrchestrationAwaitingClient(OrchestrationClientRole.Unknown)

        val server = startOrchestrationServer()
        server.connectAwaitingClient(deferred.port.awaitOrThrow())

        val client = await("Client Connection") {
            deferred.awaitOrThrow()
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
        val deferred = startOrchestrationAwaitingClient(OrchestrationClientRole.Unknown)
        server.connectAwaitingClient(deferred.port.awaitOrThrow())
        val client = deferred.awaitOrThrow()

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
        val deferred = startOrchestrationAwaitingClient(OrchestrationClientRole.Unknown)
        server.connectAwaitingClient(deferred.port.awaitOrThrow())
        val client = deferred.awaitOrThrow()

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
}
