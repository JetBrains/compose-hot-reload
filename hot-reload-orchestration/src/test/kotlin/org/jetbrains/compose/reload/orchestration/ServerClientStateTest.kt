/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.orchestration

import org.jetbrains.compose.reload.core.awaitOrThrow
import org.jetbrains.compose.reload.core.getOrThrow
import org.jetbrains.compose.reload.core.testFixtures.runStressTest
import org.jetbrains.compose.reload.orchestration.OrchestrationClientRole.Unknown
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import kotlin.test.Test
import kotlin.test.assertEquals

@Execution(ExecutionMode.SAME_THREAD)
class ServerClientStateTest {
    @Test
    fun `test - simple client based update`() = runStressTest {
        val server = startOrchestrationServer()
        val client = connectOrchestrationClient(Unknown, server.port.awaitOrThrow()).getOrThrow()
        use(server)
        use(client)

        val stateA = stateKey(
            name = "a", default = TestOrchestrationState(0)
        )

        val stateB = stateKey(
            name = "b", default = TestOrchestrationState(0)
        )

        client.update(stateA) { current ->
            TestOrchestrationState(current.payload + 1)
        }

        assertEquals(1, client.states.get(stateA).value.payload)
        assertEquals(0, client.states.get(stateB).value.payload)

        client.update(stateB) { current ->
            TestOrchestrationState(current.payload + 1)
        }

        assertEquals(1, client.states.get(stateB).value.payload)
        assertEquals(1, server.states.get(stateB).value.payload)
    }

    @Test
    fun `test - simple server based update`() = runStressTest {
        val server = startOrchestrationServer()
        val client = connectOrchestrationClient(Unknown, server.port.awaitOrThrow()).getOrThrow()
        use(server)
        use(client)

        val stateA = stateKey(
            name = "a", default = TestOrchestrationState(0)
        )

        val stateB = stateKey(
            name = "b", default = TestOrchestrationState(0)
        )

        server.update(stateA) { current ->
            TestOrchestrationState(current.payload + 1)
        }

        assertEquals(1, server.states.get(stateA).value.payload)
        assertEquals(0, server.states.get(stateB).value.payload)

        server.update(stateB) { current ->
            TestOrchestrationState(current.payload + 1)
        }

        assertEquals(1, server.states.get(stateA).value.payload)
        assertEquals(1, server.states.get(stateB).value.payload)

        // Check if the client received the update
        assertEquals(1, client.states.get(stateA).value.payload)
        assertEquals(1, client.states.get(stateB).value.payload)
    }

    @Test
    fun `test - nullable state`() = runStressTest {
        val server = startOrchestrationServer()
        val client = connectOrchestrationClient(Unknown, server.port.awaitOrThrow()).getOrThrow()
        use(server)
        use(client)

        val stateKey = stateKey<TestOrchestrationState?>(default = null)

        assertEquals(client.states.get(stateKey).value, null)
        assertEquals(server.states.get(stateKey).value, null)

        client.update(stateKey) { TestOrchestrationState(0) }
        assertEquals(0, server.states.get(stateKey).value?.payload)
        assertEquals(0, client.states.get(stateKey).value?.payload)

        server.update(stateKey) { current -> current?.copy(payload = current.payload + 1) }
        assertEquals(1, server.states.get(stateKey).value?.payload)
        client.update(stateKey) { current -> current } // Force refresh of the state
        assertEquals(1, client.states.get(stateKey).value?.payload)
    }

    @Test
    fun `test - server and client updates`() = runStressTest {
        val server = startOrchestrationServer()
        val client = connectOrchestrationClient(Unknown, server.port.awaitOrThrow()).getOrThrow()
        use(server)
        use(client)

        val stateKey = stateKey(
            default = TestOrchestrationState(0)
        )

        client.update(stateKey) { current ->
            TestOrchestrationState(current.payload + 1)
        }

        assertEquals(1, client.states.get(stateKey).value.payload)
        assertEquals(1, server.states.get(stateKey).value.payload)

        server.update(stateKey) { current ->
            TestOrchestrationState(current.payload + 1)
        }

        client.update(stateKey) { current ->
            TestOrchestrationState(current.payload + 1)
        }

        assertEquals(3, client.states.get(stateKey).value.payload)
        assertEquals(3, server.states.get(stateKey).value.payload)

        server.update(stateKey) { current ->
            TestOrchestrationState(current.payload + 1)
        }

        client.update(stateKey) { current ->
            TestOrchestrationState(current.payload + 1)
        }

        assertEquals(5, client.states.get(stateKey).value.payload)
        assertEquals(5, server.states.get(stateKey).value.payload)
    }
}
