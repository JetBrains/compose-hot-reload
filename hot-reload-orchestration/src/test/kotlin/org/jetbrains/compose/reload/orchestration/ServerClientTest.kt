/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.orchestration

import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.reload.orchestration.OrchestrationClientRole.Unknown
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ClientConnected
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ClientDisconnected
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.LogMessage
import org.junit.jupiter.api.AfterEach
import java.util.Collections.synchronizedList
import kotlin.test.Test
import kotlin.test.assertEquals

class ServerClientTest {

    private val resources = mutableListOf<AutoCloseable>()

    fun <T : AutoCloseable> use(value: T): T {
        resources.add(value)
        return value
    }

    @AfterEach
    fun cleanup() {
        resources.forEach { it.close() }
    }

    @Test
    fun `test - simple ping pong`() = runTest {
        val server = use(startOrchestrationServer())
        val serverMessages = server.asChannel()

        val client = use(connectOrchestrationClient(Unknown, server.port))

        val clientMessages = client.asChannel()

        val serverReceivedMessages = mutableListOf<OrchestrationMessage>()
        val clientReceivedMessages = mutableListOf<OrchestrationMessage>()

        server.invokeWhenMessageReceived { message ->
            if (message is ClientConnected) return@invokeWhenMessageReceived
            serverReceivedMessages.add(message)
        }

        client.invokeWhenMessageReceived { message ->
            if (message is ClientConnected) return@invokeWhenMessageReceived
            clientReceivedMessages.add(message)
        }

        client.sendMessage(LogMessage("A")).get()

        while (true) {
            if (clientMessages.receive() is LogMessage) break
        }

        while (true) {
            if (serverMessages.receive() is LogMessage) break
        }

        orchestrationThread.submit {
            assertEquals(listOf(LogMessage("A")), serverReceivedMessages.toList())
            assertEquals(listOf(LogMessage("A")), clientReceivedMessages.toList())
        }.get()
    }


    @Test
    fun `test - single shot request`() = runTest {
        val server = use(startOrchestrationServer())
        val client = use(connectOrchestrationClient(Unknown, server.port))
        val serverMessages = server.asChannel()

        val serverReceivedMessages = synchronizedList(mutableListOf<OrchestrationMessage>())
        val clientReceivedMessages = synchronizedList(mutableListOf<OrchestrationMessage>())

        server.invokeWhenMessageReceived { message ->
            if (message is ClientConnected) return@invokeWhenMessageReceived
            if (message is ClientDisconnected) return@invokeWhenMessageReceived
            serverReceivedMessages.add(message)
        }

        client.invokeWhenMessageReceived { message ->
            if (message is ClientConnected) return@invokeWhenMessageReceived
            if (message is ClientDisconnected) return@invokeWhenMessageReceived
            clientReceivedMessages.add(message)
        }

        client.use { client ->
            client.sendMessage(LogMessage("A"))
        }

        val logMessage = serverMessages.receiveAsFlow().filterIsInstance<LogMessage>().first()
        assertEquals("A", logMessage.message)

        orchestrationThread.submit {
            assertEquals(listOf(LogMessage("A")), serverReceivedMessages.toList())
        }.get()
    }


    @Test
    fun `test - multiple messages`() = runTest {
        val server = use(startOrchestrationServer())
        val client = use(connectOrchestrationClient(Unknown, server.port))
        val serverChannel = server.asChannel()
        val clientChannel = client.asChannel()

        launch {
            val firstLog = serverChannel.receiveAsFlow().filterIsInstance<LogMessage>().first()
            assertEquals("A", firstLog.message)

            val secondLog = serverChannel.receiveAsFlow().filterIsInstance<LogMessage>().first()
            assertEquals("B", secondLog.message)
        }


        launch {
            val firstLog = clientChannel.receiveAsFlow().filterIsInstance<LogMessage>().first()
            assertEquals("A", firstLog.message)

            val secondLog = clientChannel.receiveAsFlow().filterIsInstance<LogMessage>().first()
            assertEquals("B", secondLog.message)
        }

        client.sendMessage(LogMessage("A"))
        client.sendMessage(LogMessage("B"))
    }

    @Test
    fun `test - client connected and client disconnected messages`() = runTest {
        val server = use(startOrchestrationServer())
        val serverChannel = server.asChannel()

        val client = use(connectOrchestrationClient(Unknown, server.port))

        val connected = serverChannel.receiveAsFlow().filterIsInstance<ClientConnected>().first()
        assertEquals(client.clientId, connected.clientId)
        assertEquals(Unknown, connected.clientRole)

        client.close()

        val disconnected = serverChannel.receiveAsFlow().filterIsInstance<ClientDisconnected>().first()
        assertEquals(client.clientId, disconnected.clientId)
        assertEquals(Unknown, disconnected.clientRole)
    }
}
