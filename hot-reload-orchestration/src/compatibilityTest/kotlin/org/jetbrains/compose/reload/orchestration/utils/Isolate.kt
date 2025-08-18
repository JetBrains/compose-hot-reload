/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.orchestration.utils

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.future.await
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import org.jetbrains.compose.reload.core.Future
import org.jetbrains.compose.reload.core.Logger
import org.jetbrains.compose.reload.core.Os
import org.jetbrains.compose.reload.core.Queue
import org.jetbrains.compose.reload.core.complete
import org.jetbrains.compose.reload.core.completeExceptionally
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.destroyWithDescendants
import org.jetbrains.compose.reload.core.info
import org.jetbrains.compose.reload.core.issueNewDebugSessionJvmArguments
import org.jetbrains.compose.reload.orchestration.OrchestrationHandle
import java.io.EOFException
import java.io.File
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.toPath

interface IsolateMessage : Serializable {
    data object Stop : IsolateMessage {
        fun readResolve(): Any = Stop
    }
}

interface IsolateContext {
    val logger: Logger
    val messages: Queue<IsolateMessage>
    suspend fun send(message: IsolateMessage)
    suspend fun stop()
}

interface IsolateHandle : IsolateContext {
    val exitCode: Future<Int>
}

context(ctx: IsolateContext)
fun log(message: String) = ctx.logger.info(message)

context(ctx: IsolateContext)
suspend fun IsolateMessage.send() = ctx.send(this)

context(ctx: IsolateContext)
suspend fun receive(): IsolateMessage = ctx.messages.receive()

context(ctx: IsolateContext)
suspend inline fun <reified T : IsolateMessage> receiveAs(): T = receive() as T

context(_: IsolateContext)
val currentJar: Path
    get() = OrchestrationHandle::class.java.protectionDomain.codeSource.location.toURI().toPath()

interface Isolate {
    context(ctx: IsolateContext)
    suspend fun run()
}

fun CoroutineScope.launchIsolate(
    clazz: Class<out Isolate>, classpath: List<Path>
): IsolateHandle {
    val previousClasspath = classpath + System.getProperty("testClasspath").split(File.pathSeparator).map { Path(it) }

    val javaHome = Path(System.getProperty("java.home"))
    val java = javaHome.resolve(if (Os.current() == Os.Windows) "bin/java.exe" else "bin/java")

    val process = ProcessBuilder(
        java.absolutePathString(),
        "-cp", previousClasspath.joinToString(File.pathSeparator) { it.absolutePathString() },
        "-Xmx64M", "-Xms64M",
        *issueNewDebugSessionJvmArguments("isolate"),
        IsolateRunner::class.java.name, clazz.name,
    ).start()

    coroutineContext.job.invokeOnCompletion { process.destroyWithDescendants() }

    val exitCode = Future<Int>()
    val incomingMessages = Queue<IsolateMessage>()
    val outgoingMessages = Queue<IsolateMessage>()

    val isolateJob = launch(Dispatchers.IO + CoroutineName("Isolate-IO")) {
        val shutdownHook = Thread {
            process.destroyWithDescendants()
        }

        /* Manage shutdown hook */
        Runtime.getRuntime().addShutdownHook(shutdownHook)
        launch {
            process.onExit().await()
            Runtime.getRuntime().removeShutdownHook(shutdownHook)
        }

        /* Handle exit code future */
        launch {
            currentCoroutineContext().job.invokeOnCompletion { error ->
                exitCode.completeExceptionally(error ?: IllegalStateException("Isolate exited"))
            }
            process.onExit().await()
            exitCode.complete(process.exitValue())
        }

        /* Forward logs */
        launch {
            process.errorStream.copyTo(System.out)
        }

        /* Read incoming messages */
        launch {
            ObjectInputStream(process.inputStream).use { input ->
                try {
                    while (true) {
                        val message = input.readObject() as IsolateMessage
                        incomingMessages.send(message)
                    }
                } catch (_: EOFException) {
                }
            }
        }

        /* Write outgoing messages */
        launch {
            ObjectOutputStream(process.outputStream).use { output ->
                while (true) {
                    val message = outgoingMessages.receive()
                    output.writeObject(message)
                    output.flush()
                }
            }
        }
    }

    return object : IsolateHandle {
        override val logger: Logger = createLogger(clazz.name)
        override val messages: Queue<IsolateMessage> = incomingMessages
        override val exitCode: Future<Int> = exitCode

        override suspend fun send(message: IsolateMessage) {
            outgoingMessages.send(message)
        }

        override suspend fun stop() {
            if (isolateJob.isActive) outgoingMessages.send(IsolateMessage.Stop)
            isolateJob.cancelAndJoin()
        }
    }
}
