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
import org.jetbrains.compose.reload.core.currentTask
import org.jetbrains.compose.reload.core.getOrThrow
import org.jetbrains.compose.reload.orchestration.OrchestrationClientRole
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ClientConnected
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.TestEvent
import org.jetbrains.compose.reload.orchestration.asChannel
import org.jetbrains.compose.reload.orchestration.connectOrchestrationClient
import org.jetbrains.compose.reload.orchestration.startOrchestrationServer
import org.jetbrains.compose.reload.orchestration.tests.ClientForwardCompatibilityTest.SingleClientSingleEvent.ServerPort
import org.jetbrains.compose.reload.orchestration.utils.Isolate
import org.jetbrains.compose.reload.orchestration.utils.IsolateContext
import org.jetbrains.compose.reload.orchestration.utils.IsolateHandle
import org.jetbrains.compose.reload.orchestration.utils.IsolateMessage
import org.jetbrains.compose.reload.orchestration.utils.IsolateTest
import org.jetbrains.compose.reload.orchestration.utils.await
import org.jetbrains.compose.reload.orchestration.utils.launch
import org.jetbrains.compose.reload.orchestration.utils.log
import org.jetbrains.compose.reload.orchestration.utils.receive
import org.jetbrains.compose.reload.orchestration.utils.receiveAs
import org.jetbrains.compose.reload.orchestration.utils.runIsolateTest
import org.jetbrains.compose.reload.orchestration.utils.send
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@Execution(ExecutionMode.SAME_THREAD)
class ClientForwardCompatibilityTest {
    class SingleClientSingleEvent : Isolate {
        class ServerPort(val port: Int) : IsolateMessage

        context(ctx: IsolateContext)
        override suspend fun run() {
            val port = receiveAs<ServerPort>().port

            log("Connecting to server on port '$port'...")
            val client = connectOrchestrationClient(OrchestrationClientRole.Unknown, port).getOrThrow()

            log("Sending 'Hello' event")
            client.send(TestEvent("Hello"))
        }
    }

    @IsolateTest(SingleClientSingleEvent::class)
    context(_: IsolateHandle)
    fun `test - receive event`() = runIsolateTest {
        val server = startOrchestrationServer()
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
            val clientA = connectOrchestrationClient(OrchestrationClientRole.Unknown, port).getOrThrow()
            val clientB = connectOrchestrationClient(OrchestrationClientRole.Unknown, port).getOrThrow()

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

            clientA.send(TestEvent("Hello"))
            clientB.send(TestEvent("World"))
        }
    }

    @IsolateTest(MultipleClients::class)
    context(_: IsolateHandle)
    fun `test - multiple clients`() = runIsolateTest {
        val server = startOrchestrationServer()
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
}
