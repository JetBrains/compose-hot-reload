/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.orchestration

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.jetbrains.compose.reload.core.await
import org.jetbrains.compose.reload.core.awaitIdle
import org.jetbrains.compose.reload.core.awaitOrThrow
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.debug
import org.jetbrains.compose.reload.core.getOrThrow
import org.jetbrains.compose.reload.core.reloadMainThread
import org.jetbrains.compose.reload.core.withAsyncTrace
import org.jetbrains.compose.reload.orchestration.OrchestrationClientRole.Application
import org.jetbrains.compose.reload.orchestration.OrchestrationClientRole.Tooling
import org.jetbrains.compose.reload.orchestration.OrchestrationConnectionsState.Connection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail
import kotlin.time.Duration.Companion.seconds

class OrchestrationConnectionsStateTest {
    private val logger = createLogger()

    @Test
    fun `test - encode decode`() {
        val encoder = encoderOfOrThrow<OrchestrationConnectionsState>()
        val empty = OrchestrationConnectionsState(emptyList())
        assertEquals(empty, encoder.decode(encoder.encode(empty)).getOrThrow())

        val nonEmpty = OrchestrationConnectionsState(
            listOf(
                Connection(
                    clientId = OrchestrationClientId.random(),
                    clientRole = OrchestrationClientRole.Unknown,
                ),
                Connection(
                    clientId = OrchestrationClientId.random(),
                    clientRole = Tooling
                ),
                Connection(
                    clientId = OrchestrationClientId.random(),
                    clientRole = Application,
                    clientPid = 12345
                )
            )
        )

        assertEquals(nonEmpty, encoder.decode(encoder.encode(nonEmpty)).getOrThrow())
    }

    @Test
    fun `test - connections`() = runTest {
        val server = startOrchestrationServer()
        currentCoroutineContext().job.invokeOnCompletion { server.close() }

        val state = server.states.get(OrchestrationConnectionsState)
        if (state.value.connections.isNotEmpty()) {
            fail("Expected empty connections list, but got: ${state.value.connections}")
        }

        val myPid = ProcessHandle.current().pid()
        val clientA = connectOrchestrationClient(Tooling, server.port.awaitOrThrow()).getOrThrow()
        val clientB = connectOrchestrationClient(Application, server.port.awaitOrThrow()).getOrThrow()

        reloadMainThread.awaitIdle()

        withTimeout("Awaiting connections") {
            state.await { state ->
                logger.debug("Connections: ${state.connections}")
                setOf(
                    Connection(clientA.clientId, clientA.clientRole, myPid),
                    Connection(clientB.clientId, clientB.clientRole, myPid)
                ) == state.connections.toSet()
            }
        }

        clientB.close()
        withTimeout("Awaiting clientB to be disconnected") {
            state.await { state ->
                setOf(Connection(clientA.clientId, clientA.clientRole, myPid)) == state.connections.toSet()
            }
        }

        clientA.close()
        withTimeout("Awaiting clientA to be disconnected") {
            state.await { state ->
                state.connections.isEmpty()
            }
        }
    }

    private suspend fun withTimeout(title: String, block: suspend () -> Unit) {
        withAsyncTrace(title) {
            withContext(Dispatchers.IO) {
                withTimeout(5.seconds) {
                    block()
                }
            }
        }
    }
}
