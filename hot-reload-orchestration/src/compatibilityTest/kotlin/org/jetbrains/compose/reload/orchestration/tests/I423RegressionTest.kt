/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.orchestration.tests

import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import org.jetbrains.compose.reload.core.getBlocking
import org.jetbrains.compose.reload.core.getOrThrow
import org.jetbrains.compose.reload.core.stopCollecting
import org.jetbrains.compose.reload.orchestration.OrchestrationClient
import org.jetbrains.compose.reload.orchestration.OrchestrationClientRole.Unknown
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ClientConnected
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ReloadClassesRequest
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.TestEvent
import org.jetbrains.compose.reload.orchestration.OrchestrationServer
import org.jetbrains.compose.reload.orchestration.asChannel
import org.jetbrains.compose.reload.orchestration.connectBlocking
import org.jetbrains.compose.reload.orchestration.connectOrchestrationClient
import org.jetbrains.compose.reload.orchestration.utils.Isolate
import org.jetbrains.compose.reload.orchestration.utils.IsolateContext
import org.jetbrains.compose.reload.orchestration.utils.IsolateMessage
import org.jetbrains.compose.reload.orchestration.utils.IsolateTest
import org.jetbrains.compose.reload.orchestration.utils.IsolateTestFixture
import org.jetbrains.compose.reload.orchestration.utils.await
import org.jetbrains.compose.reload.orchestration.utils.receiveAs
import org.jetbrains.compose.reload.orchestration.utils.runIsolateTest
import org.jetbrains.compose.reload.orchestration.utils.send
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import java.io.File
import java.net.URLClassLoader
import kotlin.concurrent.thread
import kotlin.io.path.Path
import kotlin.system.exitProcess
import kotlin.test.assertEquals
import kotlin.test.fail
import kotlin.time.Duration.Companion.seconds

/**
 * We found this regression while developing Compose Hot Reload 1.1:
 * After some subtle changes in the protocol, the [ReloadClassesRequest] sent to 'older' clients
 * used a map built from [buildMap]: This `MapBuilder` returned by the Kotlin stdlib can cause
 * issues in Java Serialization in more complex applications (as in IntelliJ) where the kotlin-stdlib
 * is part of the System ClassLoader, but compose hot reload libraries are loaded in 'plugin ClassLoaders'.
 *
 * To reproduce the issue, we need:
 * A) A server with a modern protocol version
 * B) A client with a modern protocol version
 * C) A client with an older version of the protocol (isolate)
 *
 * The Client from B will send the request to the server. This will not use java Serialization,
 * but our own serialization format. The server used to use `buildMap` to deserialize the map in [ReloadClassesRequest].
 * Since client C only supports Java Serialization, the server will then pass this map to the Java serialization.
 *
 * The client, however, will fail to deserialize the `MapBuilder`, as the surrogate in `MapBuilder`
 * (called `SerializedMap`) is [java.io.Externalizable] and calls `readObject`, which requires the object
 * to be loaded in the same ClassLoader (which is not the case for many applications)
 */
@Execution(ExecutionMode.SAME_THREAD)
class I423RegressionTest {
    class Issue423RegressionIsolate : Isolate {
        class ServerPort(val port: Int) : IsolateMessage

        @Suppress("BlockingMethodInNonBlockingContext")
        context(ctx: IsolateContext)
        override suspend fun run() {
            val port = receiveAs<ServerPort>().port

            /*
            I know we're already in an Isolate, but we need to mimic a more complex application,
            therefore, we emulate a 'System ClassLoader' which only contains the Kotlin Stdlib
            and a 'Plugin ClassLoader' which contains everything (except the stdlib)
             */
            val classpath = System.getProperty("java.class.path")
                .split(File.pathSeparatorChar).map { Path(it).toUri().toURL() }

            val systemClassLoaderLike = URLClassLoader(
                classpath.filter { it.path.contains("kotlin-stdlib") }.toTypedArray(),
                ClassLoader.getPlatformClassLoader()
            )

            val pluginClassLoaderLike = URLClassLoader(
                classpath.filter { !it.path.contains("kotlin-stdlib") }.toTypedArray(),
                systemClassLoaderLike
            )

            /*
            We then the test code in a new thread, from the new ClassLoader.
             */
            val pluginCode = pluginClassLoaderLike.loadClass(PluginCode::class.java.name)

            val pluginThread = thread(name = "i423 Plugin Code", contextClassLoader = pluginClassLoaderLike) {
                Thread.currentThread().uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { _, e ->
                    e.printStackTrace(System.err)
                    exitProcess(1)
                }
                pluginCode.getMethod("run", Int::class.javaPrimitiveType).invoke(null, port)
            }

            pluginThread.join()
        }

        object PluginCode {
            @JvmStatic
            @Suppress("unused") // reflection!
            fun run(port: Int) {
                System.err.println("'PluginCode': connecting to server")
                val client = OrchestrationClient(Unknown, port).connectBlocking().getOrThrow()
                client.subtask {
                    client.send(TestEvent("#423 Ready"))

                    System.err.println("'PluginCode': receiving message")
                    client.messages.collect { message ->
                        if (message !is ReloadClassesRequest) return@collect
                        if (message.changedClassFiles.isEmpty()) fail(".changedClassFiles list is empty")

                        System.err.println("'PluginCode': sending OK acknowledgement")
                        client.send(TestEvent("#423 OK"))
                        stopCollecting()
                    }
                    client.close()
                    client.await()
                }.getBlocking(15.seconds).getOrThrow()
            }
        }
    }

    @IsolateTest(Issue423RegressionIsolate::class)
    context(_: IsolateTestFixture)
    fun `test - regression #423 - MapBuilder and SerializedMap`() = runIsolateTest {
        val server = OrchestrationServer()
        server.start()

        val messages = server.asChannel()
        Issue423RegressionIsolate.ServerPort(server.port.await().getOrThrow()).send()

        /* Await connection */
        await("client connection") {
            messages.receiveAsFlow().filterIsInstance<ClientConnected>().first()
            assertEquals("#423 Ready", messages.receiveAsFlow().filterIsInstance<TestEvent>().first().payload)
        }

        /* Create another modern client */
        val client = connectOrchestrationClient(Unknown, server.port.await().getOrThrow()).getOrThrow()

        /* Send the 'problematic message' and expect the OK response! */
        client send ReloadClassesRequest(
            changedClassFiles = mapOf(File("abc") to ReloadClassesRequest.ChangeType.Modified)
        )

        /*
        Await the 'OK' from our Isolate!
         */
        val ok = await("Await OK response") {
            messages.receiveAsFlow().filterIsInstance<TestEvent>().first().payload
        }

        assertEquals("#423 OK", ok)
    }
}
