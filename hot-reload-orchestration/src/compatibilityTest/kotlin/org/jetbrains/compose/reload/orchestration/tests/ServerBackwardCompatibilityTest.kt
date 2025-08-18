/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.orchestration.tests

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import org.jetbrains.compose.reload.core.awaitIdle
import org.jetbrains.compose.reload.core.awaitOrThrow
import org.jetbrains.compose.reload.core.currentTask
import org.jetbrains.compose.reload.core.getOrThrow
import org.jetbrains.compose.reload.core.reloadMainThread
import org.jetbrains.compose.reload.core.withType
import org.jetbrains.compose.reload.orchestration.OrchestrationClientRole.Unknown
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ClientConnected
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.TestEvent
import org.jetbrains.compose.reload.orchestration.OrchestrationServer
import org.jetbrains.compose.reload.orchestration.asChannel
import org.jetbrains.compose.reload.orchestration.connectOrchestrationClient
import org.jetbrains.compose.reload.orchestration.tests.ServerBackwardCompatibilityTest.SingleClientSingleEvent.ServerPort
import org.jetbrains.compose.reload.orchestration.utils.Isolate
import org.jetbrains.compose.reload.orchestration.utils.IsolateContext
import org.jetbrains.compose.reload.orchestration.utils.IsolateMessage
import org.jetbrains.compose.reload.orchestration.utils.IsolateTest
import org.jetbrains.compose.reload.orchestration.utils.IsolateTestFixture
import org.jetbrains.compose.reload.orchestration.utils.MinSupportedVersion
import org.jetbrains.compose.reload.orchestration.utils.TestOrchestrationState
import org.jetbrains.compose.reload.orchestration.utils.await
import org.jetbrains.compose.reload.orchestration.utils.launch
import org.jetbrains.compose.reload.orchestration.utils.log
import org.jetbrains.compose.reload.orchestration.utils.receive
import org.jetbrains.compose.reload.orchestration.utils.receiveAs
import org.jetbrains.compose.reload.orchestration.utils.runIsolateTest
import org.jetbrains.compose.reload.orchestration.utils.send
import org.jetbrains.compose.reload.orchestration.utils.stateKey
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import kotlin.test.assertEquals

@Execution(ExecutionMode.SAME_THREAD)
class ServerBackwardCompatibilityTest {
    class SingleClientSingleEvent : Isolate {
        class ServerPort(val port: Int) : IsolateMessage

        context(ctx: IsolateContext)
        override suspend fun run() {
            val port = receiveAs<ServerPort>().port

            log("Connecting to server on port '$port'...")
            val client = connectOrchestrationClient(Unknown, port).getOrThrow()

            log("Sending 'Hello' event")
            client.send(TestEvent("Hello"))
        }
    }

    @IsolateTest(SingleClientSingleEvent::class)
    context(_: IsolateTestFixture)
    fun `test - receive event`() = runIsolateTest {
        val server = OrchestrationServer()
        server.start()

        val messages = server.asChannel()
        ServerPort(server.port.await().getOrThrow()).send()

        // Await client connection
        await("'ClientConnected' message") {
            messages.receiveAsFlow().filterIsInstance<ClientConnected>().first()
        }

        // Await event
        await("'TestEvent' from client") {
            messages.receiveAsFlow().first { it is TestEvent && it.payload == "Hello" }
        }
    }

    class MultipleClients : Isolate {
        class ServerPort(val port: Int) : IsolateMessage
        class ClientAReceivedMessage(val message: OrchestrationMessage) : IsolateMessage
        class ClientBReceivedMessage(val message: OrchestrationMessage) : IsolateMessage

        context(ctx: IsolateContext)
        override suspend fun run() {
            val port = receiveAs<ServerPort>().port
            val clientA = connectOrchestrationClient(Unknown, port).getOrThrow()
            val clientB = connectOrchestrationClient(Unknown, port).getOrThrow()

            currentTask().subtask {
                clientA.messages.collect {
                    ClientAReceivedMessage(it).send()
                }
            }

            currentTask().subtask {
                clientB.messages.collect {
                    ClientBReceivedMessage(it).send()
                }
            }

            reloadMainThread.awaitIdle()
            clientA.send(TestEvent("Hello"))
            clientB.send(TestEvent("World"))
        }
    }

    @IsolateTest(MultipleClients::class)
    context(_: IsolateTestFixture)
    fun `test - multiple clients`() = runIsolateTest {
        val server = OrchestrationServer()
        server.start()

        val connectionMessages = server.asChannel()
        val messages = server.asChannel()
        val clientAReceivedMessages = Channel<MultipleClients.ClientAReceivedMessage>(Channel.UNLIMITED)
        val clientBReceivedMessages = Channel<MultipleClients.ClientBReceivedMessage>(Channel.UNLIMITED)

        val receiveIsolateMessages = launch {
            while (isActive) {
                when (val message = receive()) {
                    is MultipleClients.ClientAReceivedMessage -> clientAReceivedMessages.send(message)
                    is MultipleClients.ClientBReceivedMessage -> clientBReceivedMessages.send(message)
                    else -> continue
                }
            }
        }

        MultipleClients.ServerPort(server.port.await().getOrThrow()).send()

        await("first client connection") {
            connectionMessages.receiveAsFlow().filterIsInstance<ClientConnected>().first()
        }

        await("second client connection") {
            connectionMessages.receiveAsFlow().filterIsInstance<ClientConnected>().first()
        }

        await("'Hello' from first client") {
            messages.receiveAsFlow().first { it is TestEvent && it.payload == "Hello" }
        }

        await("'World' from second client") {
            messages.receiveAsFlow().first { it is TestEvent && it.payload == "World" }
        }

        await("Confirm clientA received all messages") {
            clientAReceivedMessages.receiveAsFlow().first { it.message is TestEvent && it.message.payload == "Hello" }
            clientAReceivedMessages.receiveAsFlow().first { it.message is TestEvent && it.message.payload == "World" }
        }

        await("Confirm clientB received all messages") {
            clientBReceivedMessages.receiveAsFlow().first { it.message is TestEvent && it.message.payload == "Hello" }
            clientBReceivedMessages.receiveAsFlow().first { it.message is TestEvent && it.message.payload == "World" }
        }

        receiveIsolateMessages.cancel()
    }

    class EchoClient : Isolate {
        class ServerPort(val port: Int) : IsolateMessage
        data object Ready : IsolateMessage {
            fun readResolve(): Any = Ready
        }

        context(ctx: IsolateContext)
        override suspend fun run() {
            val client = connectOrchestrationClient(Unknown, receiveAs<EchoClient.ServerPort>().port).getOrThrow()
            log("Client connected (${client.port.awaitOrThrow()})")

            currentTask().subtask {
                client.messages.withType<TestEvent>().collect {
                    if (it.payload is String && it.payload.startsWith("Echo:")) return@collect
                    client.send(TestEvent("Echo: ${it.payload}"))
                    log("Sent Echo: ${it.payload}")
                }
            }

            reloadMainThread.awaitIdle()

            log("Client ready")
            Ready.send()
        }
    }

    @IsolateTest(EchoClient::class)
    context(_: IsolateTestFixture)
    fun `test - update state - echo is still alive`() = runIsolateTest {
        val server = OrchestrationServer()
        server.start()
        val messages = server.asChannel()

        EchoClient.ServerPort(server.port.await().getOrThrow()).send()
        await("client connection") {
            messages.receiveAsFlow().filterIsInstance<ClientConnected>().first()
        }

        await("client ready") {
            receiveAs<EchoClient.Ready>()
        }

        await("client echo") {
            server.send(TestEvent("Foo"))
            messages.receiveAsFlow().first { it is TestEvent && it.payload == "Echo: Foo" }
        }

        assertEquals(0, server.states.get(stateKey).value.payload)

        await("state update") {
            server.update(stateKey) { current -> TestOrchestrationState(current.payload + 1) }
            assertEquals(1, server.states.get(stateKey).value.payload)
        }

        await("client echo") {
            server.send(TestEvent("Bar"))
            messages.receiveAsFlow().first { it is TestEvent && it.payload == "Echo: Bar" }
        }
    }

    class StateEchoClient : Isolate {
        class ServerPort(val port: Int) : IsolateMessage

        context(ctx: IsolateContext)
        override suspend fun run() {
            val client = connectOrchestrationClient(Unknown, receiveAs<StateEchoClient.ServerPort>().port).getOrThrow()
            currentTask().subtask {
                client.states.get(stateKey).collect { state ->
                    client send TestEvent(state)
                }
            }
        }
    }

    @IsolateTest(StateEchoClient::class)
    @MinSupportedVersion("1.0.0-rc01")
    context(_: IsolateTestFixture)
    fun `test - state echo`() = runIsolateTest {
        val server = OrchestrationServer()
        server.start()
        val messages = server.asChannel()

        StateEchoClient.ServerPort(server.port.await().getOrThrow()).send()

        await("client connection") {
            messages.receiveAsFlow().filterIsInstance<ClientConnected>().first()
        }

        await("await initial state") {
            messages.receiveAsFlow().first { it is TestEvent && it.payload == stateKey.default }
        }

        await("update state (1)") {
            server.update(stateKey) { current -> TestOrchestrationState(current.payload + 1) }
            assertEquals(
                TestOrchestrationState(1),
                messages.receiveAsFlow().filterIsInstance<TestEvent>().first().payload
            )
        }

        await("update state (2)") {
            server.update(stateKey) { current -> TestOrchestrationState(current.payload + 1) }
            assertEquals(
                TestOrchestrationState(2),
                messages.receiveAsFlow().filterIsInstance<TestEvent>().first().payload
            )
        }
    }
}
