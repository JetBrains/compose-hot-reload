/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.orchestration.tests

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.job
import org.jetbrains.compose.reload.core.await
import org.jetbrains.compose.reload.core.awaitIdle
import org.jetbrains.compose.reload.core.awaitOrThrow
import org.jetbrains.compose.reload.core.currentCoroutineContext
import org.jetbrains.compose.reload.core.getOrThrow
import org.jetbrains.compose.reload.core.reloadMainThread
import org.jetbrains.compose.reload.orchestration.OrchestrationClient
import org.jetbrains.compose.reload.orchestration.OrchestrationClientRole.Unknown
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.InvalidatedComposeGroupMessage
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.InvalidatedComposeGroupMessage.DirtyScope
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.TestEvent
import org.jetbrains.compose.reload.orchestration.OrchestrationServer
import org.jetbrains.compose.reload.orchestration.asChannel
import org.jetbrains.compose.reload.orchestration.connectOrchestrationClient
import org.jetbrains.compose.reload.orchestration.startOrchestrationListener
import org.jetbrains.compose.reload.orchestration.stateKey
import org.jetbrains.compose.reload.orchestration.tests.ServerForwardCompatibilityTest.StartServer.ServerPort
import org.jetbrains.compose.reload.orchestration.utils.Isolate
import org.jetbrains.compose.reload.orchestration.utils.IsolateContext
import org.jetbrains.compose.reload.orchestration.utils.IsolateMessage
import org.jetbrains.compose.reload.orchestration.utils.IsolateTest
import org.jetbrains.compose.reload.orchestration.utils.IsolateTestFixture
import org.jetbrains.compose.reload.orchestration.utils.MinSupportedVersion
import org.jetbrains.compose.reload.orchestration.utils.TestOrchestrationState
import org.jetbrains.compose.reload.orchestration.utils.await
import org.jetbrains.compose.reload.orchestration.utils.currentJar
import org.jetbrains.compose.reload.orchestration.utils.log
import org.jetbrains.compose.reload.orchestration.utils.receiveAs
import org.jetbrains.compose.reload.orchestration.utils.runIsolateTest
import org.jetbrains.compose.reload.orchestration.utils.send
import org.jetbrains.compose.reload.orchestration.utils.stateKey
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import kotlin.test.assertEquals


/**
 * Tests if an old server version is happy to accept connections from the current client version
 * (hence, if the old server is forward compatible with the new client)
 */
@Execution(ExecutionMode.SAME_THREAD)
class ServerForwardCompatibilityTest {

    class StartServer : Isolate {
        data class ServerPort(val port: Int) : IsolateMessage

        context(ctx: IsolateContext)
        override suspend fun run() {
            log("Starting server... (${currentJar.fileName})")

            val server = OrchestrationServer()
            server.start()

            val port = server.port.await().getOrThrow()
            log("Server started on port '$port'")
            ServerPort(port).send()

            server.await()
        }
    }

    @IsolateTest(StartServer::class)
    context(_: IsolateTestFixture)
    fun `test - send and receive TestEvent`() = runIsolateTest {
        val port = receiveAs<ServerPort>().port

        val client = OrchestrationClient(Unknown, port)
        val messages = client.asChannel()
        client.connect().getOrThrow()
        client.send(TestEvent("Hello"))

        await("TestEvent echo from server") {
            messages.receiveAsFlow().first { it is TestEvent && it.payload == "Hello" }
        }
    }

    @IsolateTest(StartServer::class)
    context(_: IsolateTestFixture)
    fun `test - send message across two clients`() = runIsolateTest {
        val port = receiveAs<ServerPort>().port
        val clientA = OrchestrationClient(Unknown, port)
        val clientB = OrchestrationClient(Unknown, port)
        val messagesA = clientA.asChannel()
        val messagesB = clientB.asChannel()

        await("clientA & clientB connected") {
            clientA.connect().getOrThrow()
            clientB.connect().getOrThrow()
            log("clientA & clientB connected")
        }

        /* Warmup */
        await("clientA & clientB warmup") {
            clientA.send(TestEvent("Warmup"))
            messagesA.receiveAsFlow().first { it is TestEvent && it.payload == "Warmup" }

            clientB.send(TestEvent("Warmup"))
            messagesB.receiveAsFlow().first { it is TestEvent && it.payload == "Warmup" }
        }

        reloadMainThread.awaitIdle()

        /* Send a message from client A */
        clientA.send(TestEvent("Hello"))

        await("clientA received 'Hello'") {
            messagesA.receiveAsFlow().first { it is TestEvent && it.payload == "Hello" }
        }

        await("clientB received 'Hello'") {
            messagesB.receiveAsFlow().first { it is TestEvent && it.payload == "Hello" }
        }

        log("clientA & clientB received 'Hello'")

        /* Send a message from client B */
        clientB.send(TestEvent("World"))

        await("clientA received 'World'") {
            messagesA.receiveAsFlow().first { it is TestEvent && it.payload == "World" }
        }

        await("clientB received 'World'") {
            messagesB.receiveAsFlow().first { it is TestEvent && it.payload == "World" }
        }

        log("clientA & clientB received 'World'")
    }

    @IsolateTest(StartServer::class)
    context(_: IsolateTestFixture)
    fun `test - update a state`() = runIsolateTest {
        val port = receiveAs<ServerPort>().port
        val client = connectOrchestrationClient(Unknown, port).getOrThrow()
        log("Client connected '${client.port.awaitOrThrow()}'")

        val stateKey = stateKey<TestOrchestrationState>(TestOrchestrationState(0))

        await("Requesting state") {
            assertEquals(stateKey.default, client.states.get(stateKey).value)
        }

        await("Updating state") {
            client.update(stateKey) { current -> TestOrchestrationState(current.payload + 1) }
        }
        assertEquals(1, client.states.get(stateKey).value.payload)
    }

    @IsolateTest(StartServer::class)
    @MinSupportedVersion("1.0.0-rc01")
    context(_: IsolateTestFixture)
    fun `test - sync state across two clients`() = runIsolateTest {
        val port = receiveAs<ServerPort>().port
        val clientA = OrchestrationClient(Unknown, port)
        val clientB = OrchestrationClient(Unknown, port)
        clientA.connect().getOrThrow()
        clientB.connect().getOrThrow()

        await("update states") {
            clientA.update(stateKey) { current -> TestOrchestrationState(current.payload + 1) }
            clientB.update(stateKey) { current -> TestOrchestrationState(current.payload + 1) }
        }

        /* Enforce sync */
        await("sync states") {
            clientA.update(stateKey) { current -> current }
            clientB.update(stateKey) { current -> current }
        }

        assertEquals(2, clientA.states.get(stateKey).value.payload)
        assertEquals(2, clientB.states.get(stateKey).value.payload)
    }

    class ConnectClientServerIsolate : Isolate {
        data class ClientPort(val port: Int) : IsolateMessage

        context(ctx: IsolateContext)
        override suspend fun run() {
            val server = OrchestrationServer()
            server.start()

            val awaitingClientPort = receiveAs<ClientPort>()
            if (!server.connectClient(awaitingClientPort.port))
                throw IllegalStateException("Failed to connect to awaiting client")

            server.update(stateKey) { TestOrchestrationState(42) }
            server.await()
        }
    }

    @IsolateTest(ConnectClientServerIsolate::class)
    @MinSupportedVersion("1.0.0-rc01")
    context(_: IsolateTestFixture)
    fun `test - orchestration listener`() = runIsolateTest {
        val orchestrationListener = startOrchestrationListener(Unknown)
        currentCoroutineContext().job.invokeOnCompletion {
            orchestrationListener.close()
        }

        ConnectClientServerIsolate.ClientPort(orchestrationListener.port.awaitOrThrow()).send()

        val client = await("client connected") {
            orchestrationListener.connections.receive().getOrThrow()
        }

        await("state received") {
            client.states.get(stateKey).await { it.payload == 42 }
        }
    }

    @IsolateTest(StartServer::class)
    context(_: IsolateTestFixture)
    fun `test - client sends InvalidatedComposeGroupMessage to old server - connection survives`() = runIsolateTest {
        val port = receiveAs<ServerPort>().port

        val client = OrchestrationClient(Unknown, port)
        val messages = client.asChannel()
        client.connect().getOrThrow()

        client.send(TestEvent("Hello"))

        await("TestEvent echo from server") {
            messages.receiveAsFlow().first { it is TestEvent && it.payload == "Hello" }
        }

        val invalidation = InvalidatedComposeGroupMessage(
            groupKey = 456,
            dirtyScopes = listOf(
                DirtyScope(
                    methodName = "bar",
                    methodDescriptor = "()V",
                    classId = "com/example/Bar",
                    scopeType = DirtyScope.ScopeType.Method,
                    sourceFile = "App.kt",
                    firstLineNumber = 42,
                )
            )
        )

        client.send(invalidation)

        client.send(TestEvent("Bye"))
        await("TestEvent echo from server") {
            messages.receiveAsFlow().first { it is TestEvent && it.payload == "Bye" }
        }
    }
}
