/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.orchestration.tests

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.reload.core.getOrThrow
import org.jetbrains.compose.reload.orchestration.OrchestrationClient
import org.jetbrains.compose.reload.orchestration.OrchestrationClientRole.Unknown
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.TestEvent
import org.jetbrains.compose.reload.orchestration.OrchestrationServer
import org.jetbrains.compose.reload.orchestration.asChannel
import org.jetbrains.compose.reload.orchestration.tests.ServerForwardCompatibilityTest.StartServer.ServerPort
import org.jetbrains.compose.reload.orchestration.utils.Isolate
import org.jetbrains.compose.reload.orchestration.utils.IsolateContext
import org.jetbrains.compose.reload.orchestration.utils.IsolateHandle
import org.jetbrains.compose.reload.orchestration.utils.IsolateMessage
import org.jetbrains.compose.reload.orchestration.utils.IsolateTest
import org.jetbrains.compose.reload.orchestration.utils.await
import org.jetbrains.compose.reload.orchestration.utils.currentJar
import org.jetbrains.compose.reload.orchestration.utils.log
import org.jetbrains.compose.reload.orchestration.utils.receiveAs
import org.jetbrains.compose.reload.orchestration.utils.runIsolateTest
import org.jetbrains.compose.reload.orchestration.utils.send


/**
 * Tests if an old server version is happy to accept connections from the current client version
 * (hence, if the old server is forward compatible with the new client)
 */
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
        }
    }

    @IsolateTest(StartServer::class)
    context(_: IsolateHandle)
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
    context(_: IsolateHandle)
    fun `test - send message across two clients`() = runIsolateTest {
        val port = receiveAs<ServerPort>().port
        val clientA = OrchestrationClient(Unknown, port)
        val clientB = OrchestrationClient(Unknown, port)
        val messagesA = clientA.asChannel()
        val messagesB = clientB.asChannel()

        clientA.connect().getOrThrow()
        clientB.connect().getOrThrow()

        clientA.send(TestEvent("Hello"))
        messagesA.receiveAsFlow().first { it is TestEvent && it.payload == "Hello" }
        messagesB.receiveAsFlow().first { it is TestEvent && it.payload == "Hello" }

        clientB.send(TestEvent("World"))
        messagesA.receiveAsFlow().first { it is TestEvent && it.payload == "World" }
        messagesB.receiveAsFlow().first { it is TestEvent && it.payload == "World" }
    }
}
