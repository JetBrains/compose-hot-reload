/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

@file:OptIn(InternalCoroutinesApi::class)

package org.jetbrains.compose.reload.orchestration

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.withIndex
import kotlinx.coroutines.internal.synchronized
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.reload.core.awaitOrThrow
import org.jetbrains.compose.reload.core.getOrThrow
import org.jetbrains.compose.reload.core.invokeOnValue
import org.jetbrains.compose.reload.core.reloadMainThread
import org.jetbrains.compose.reload.core.testFixtures.runStressTest
import org.jetbrains.compose.reload.core.withThread
import org.jetbrains.compose.reload.orchestration.OrchestrationClientRole.Unknown
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ClientConnected
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ClientDisconnected
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.LogMessage
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.TestEvent
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import java.net.ServerSocket
import java.util.Collections.synchronizedList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail
import kotlin.time.Duration.Companion.seconds

@Execution(ExecutionMode.SAME_THREAD)
class ServerClientTest {

    private val resources = mutableListOf<OrchestrationHandle>()

    suspend fun <T : OrchestrationHandle> use(value: T): T {
        currentCoroutineContext().job.invokeOnCompletion { value.close() }
        synchronized(this) {
            resources.add(value)
            return value
        }
    }

    @AfterEach
    fun cleanup() = synchronized(this) {
        resources.forEach { it.close() }
    }

    @Test
    fun `test - simple ping pong`() = runTest(timeout = 10.seconds) {
        val server = use(startOrchestrationServer())
        val client = use(OrchestrationClient(Unknown, server.port.await().getOrThrow()))

        val serverChannel = server.asChannel()
        val clientChannel = client.asChannel()

        val serverReceivedMessages = mutableListOf<OrchestrationMessage>()
        val clientReceivedMessages = mutableListOf<OrchestrationMessage>()

        server.messages.invokeOnValue { message ->
            if (message is ClientConnected) return@invokeOnValue
            serverReceivedMessages.add(message)
        }

        client.messages.invokeOnValue { message ->
            if (message is ClientConnected) return@invokeOnValue
            clientReceivedMessages.add(message)
        }

        client.connect().getOrThrow()

        withThread(reloadMainThread) {
            client.send(LogMessage("A"))
            clientChannel.consumeAsFlow().filterIsInstance<LogMessage>().first()
            serverChannel.receiveAsFlow().filterIsInstance<LogMessage>().first()

            assertEquals(listOf<OrchestrationMessage>(LogMessage("A")), serverReceivedMessages)
            assertEquals(listOf<OrchestrationMessage>(LogMessage("A")), clientReceivedMessages)
        }
    }


    @Test
    fun `test - single shot request`() = runTest {
        val server = use(startOrchestrationServer())
        val client = use(connectOrchestrationClient(Unknown, server.port.awaitOrThrow()).getOrThrow())
        val serverMessages = server.asChannel()

        val serverReceivedMessages = synchronizedList(mutableListOf<OrchestrationMessage>())
        val clientReceivedMessages = synchronizedList(mutableListOf<OrchestrationMessage>())

        server.messages.invokeOnValue { message ->
            if (message is ClientConnected) return@invokeOnValue
            if (message is ClientDisconnected) return@invokeOnValue
            serverReceivedMessages.add(message)
        }

        client.messages.invokeOnValue { message ->
            if (message is ClientConnected) return@invokeOnValue
            if (message is ClientDisconnected) return@invokeOnValue
            clientReceivedMessages.add(message)
        }

        client.use { client ->
            client.send(LogMessage("A"))
        }

        val logMessage = serverMessages.receiveAsFlow().filterIsInstance<LogMessage>().first()
        assertEquals("A", logMessage.message)

        withThread(reloadMainThread) {
            assertEquals(listOf(LogMessage("A")), serverReceivedMessages.toList())
        }
    }


    @Test
    fun `test - multiple messages`() = runTest {
        val server = use(startOrchestrationServer())
        val client = use(connectOrchestrationClient(Unknown, server.port.awaitOrThrow()).getOrThrow())
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

        client.send(LogMessage("A"))
        client.send(LogMessage("B"))
    }


    @Test
    fun `test - client connected and client disconnected messages`() = runTest {
        val server = use(startOrchestrationServer())
        val serverChannel = server.asChannel()

        val client = use(OrchestrationClient(Unknown, server.port.awaitOrThrow()))
        client.connect()

        val connected = serverChannel.receiveAsFlow().filterIsInstance<ClientConnected>().first()
        assertEquals(client.clientId, connected.clientId)
        assertEquals(Unknown, connected.clientRole)

        client.close()

        val disconnected = serverChannel.receiveAsFlow().filterIsInstance<ClientDisconnected>().first()
        assertEquals(client.clientId, disconnected.clientId)
        assertEquals(Unknown, disconnected.clientRole)
    }


    @Test
    fun `stress test - incoming message flood`() = runStressTest(
        repetitions = 24, parallelism = 4
    ) {
        val senderCoroutines = 4
        val messagesPerSender = 128
        val elements = senderCoroutines * messagesPerSender

        val server = use(startOrchestrationServer())
        val client = use(connectOrchestrationClient(Unknown, server.port.awaitOrThrow()).getOrThrow())
        coroutineContext.job.invokeOnCompletion { server.close() }
        coroutineContext.job.invokeOnCompletion { client.close() }

        val incomingFlow = server.asFlow()
        val incomingChannel = server.asChannel()
        val incomingQueue = server.asBlockingQueue()

        val flowAllowCollecting = Job(currentCoroutineContext().job)
        val fromFlowAsync = async(context = Dispatchers.Unconfined) {
            incomingFlow.filterIsInstance<TestEvent>()
                .onStart { reportActivity("Starting flow collection") }
                .take(elements).withIndex().map { (index, message) ->
                    flowAllowCollecting.join()
                    reportActivity("Collecting message from flow (${message.payload}) : ${index + 1}/$elements")
                    message
                }.toList()
        }

        coroutineScope {
            repeat(senderCoroutines) { coroutineId ->
                launch(Dispatchers.IO + CoroutineName("Sender #$coroutineId")) {
                    repeat(messagesPerSender) { iterationId ->
                        reportActivity("Sending message coroutineId: $coroutineId, iterationId: $iterationId")
                        val event = TestEvent(listOf(coroutineId, iterationId))
                        client.send(event)
                    }
                }
            }
        }

        /* Allow the flow to collect messages */
        reportActivity("Allowing flow to collect messages")
        flowAllowCollecting.complete()

        reportActivity("Awaiting flow")
        fromFlowAsync.await()

        val fromChannel = incomingChannel.consumeAsFlow().filterIsInstance<TestEvent>()
            .take(elements)
            .onEach { reportActivity("Polling channel") }
            .toList()

        val fromQueue = buildList(elements) {
            while (isActive) {
                reportActivity("Polling queue")
                val event = incomingQueue.poll() ?: break
                if (event is TestEvent) add(event)
            }
        }

        val expectedEvents = (0 until senderCoroutines).flatMap { coroutineId ->
            (0 until messagesPerSender).map { iterationId ->
                TestEvent(listOf(coroutineId, iterationId))
            }
        }.toSet()

        reportActivity("Checking 'fromChannel'")
        fromChannel.toHashSet().containsAll(expectedEvents)
            || fail("Channel (${fromChannel.size}/$elements) did not receive all messages | iteration : $$invocationIndex")


        reportActivity("Checking 'fromQueue'")
        fromQueue.toHashSet().containsAll(expectedEvents)
            || fail("Queue (${fromQueue.size}/$elements)  did not receive all messages | iteration : $invocationIndex")



        reportActivity("Checking 'fromFlowAsync'")
        fromFlowAsync.await().toHashSet().containsAll(expectedEvents)
            || fail("Flow(${fromFlowAsync.await().size}) did not receive all messages | iteration : $invocationIndex")

        client.close()
    }

    @Test
    fun `test - stress test - fire and forget`() = runStressTest(
        repetitions = 12, parallelism = 2
    ) {
        val senderCoroutines = 1
        val messagesPerSender = 128
        val expectedMessages = senderCoroutines * messagesPerSender

        val server = use(startOrchestrationServer())
        val channel = server.asChannel()

        val messagesReceived = Array<CompletableDeferred<TestEvent>>(expectedMessages) { CompletableDeferred() }

        launch {
            channel.consumeAsFlow().collect { message ->
                reportActivity("Received message: $message")
                if (message is TestEvent) {
                    val index = message.payload as Int
                    val job = messagesReceived[index]
                    if (job.isCompleted) error("Already received '$message'")
                    if (!job.complete(message)) error("Already received '$message'")
                }
            }
        }

        val index = AtomicInteger(0)
        repeat(senderCoroutines) {
            launch(Dispatchers.IO) {
                repeat(messagesPerSender) {
                    val myIndex = index.andIncrement
                    reportActivity("Sending message #$myIndex")
                    connectOrchestrationClient(Unknown, server.port.awaitOrThrow()).getOrThrow().use { client ->
                        client.send(TestEvent(myIndex))
                    }
                }
            }
        }


        /* Await all clients to disconnect */
        val messages = messagesReceived.map { it.await() }
        assertEquals(expectedMessages, messages.size)
        server.close()
    }


    /**
     * Fellow Readers: You stumbled across a stress test trying to test if the 'connectOrchestrationClient'
     * is behaving properly when trying to connect to a 'bad server' under stress.
     * If you find my mind, please contact sebastian.sellmair@jetbrains.com, because I might have lost it
     * somewhere here.
     *
     * This stress test will bind a new server which will accept and immediately close connections.
     * Then multiple coroutines will try to connect to the server, expecting failures.
     */
    @Test
    fun `test - stress test - connecting to bad server`() {
        ServerSocket(0).use { serverSocket ->
            val serverThread = thread {
                while (!Thread.currentThread().isInterrupted && !serverSocket.isClosed) {
                    runCatching {
                        val socket = serverSocket.accept()
                        socket.setOrchestrationDefaults()
                        socket.close()
                    }
                }
            }

            runStressTest(
                repetitions = 128,
                parallelism = 4
            ) {
                repeat(128) {
                    reportActivity("Connecting to closed server #$it")
                    if (serverSocket.isClosed) error("ServerSocket is closed")

                    val client = try {
                        connectOrchestrationClient(Unknown, serverSocket.localPort).getOrThrow()
                    } catch (_: Throwable) {
                        return@repeat
                    }

                    try {
                        error(
                            "Unexpected connection: ${client.clientId} " +
                                "(server.isClosed: ${serverSocket.isClosed}, " +
                                "server.isBound: ${serverSocket.isBound}, " +
                                "serverThread.isAlive: ${serverThread.isAlive})"
                        )
                    } finally {
                        client.close()
                    }
                }
            }

            if (!serverThread.isAlive) fail("Expected 'serverThread.isAlive'")
            serverSocket.close()
            serverThread.interrupt()
            serverThread.join()
        }
    }
}
