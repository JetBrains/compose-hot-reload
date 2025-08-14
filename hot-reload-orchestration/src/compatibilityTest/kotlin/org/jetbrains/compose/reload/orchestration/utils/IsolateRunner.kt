/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.orchestration.utils

import org.jetbrains.compose.reload.core.Logger
import org.jetbrains.compose.reload.core.Queue
import org.jetbrains.compose.reload.core.WorkerThread
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.dispatcher
import org.jetbrains.compose.reload.core.error
import org.jetbrains.compose.reload.core.getBlocking
import org.jetbrains.compose.reload.core.getOrThrow
import org.jetbrains.compose.reload.core.info
import org.jetbrains.compose.reload.core.launchTask
import java.io.EOFException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.seconds

object IsolateRunner {
    @JvmStatic
    fun main(args: Array<String>) {
        try {
            val className = args[0]
            val clazz = Class.forName(className)
            val instance = clazz.getConstructor().newInstance() as Isolate

            val incomingMessages = Queue<IsolateMessage>()
            val outgoingMessages = Queue<IsolateMessage>()

            val logger: Logger = createLogger("$className(Isolate)")
            val writer = WorkerThread("writer")
            val reader = WorkerThread("reader")

            launchTask {
                subtask(context = reader.dispatcher) {
                    try {
                        ObjectInputStream(System.`in`).use { input ->
                            while (true) {
                                val message = input.readObject() as IsolateMessage
                                if (message == IsolateMessage.Stop) {
                                    logger.info("Stop received...")
                                    System.err.flush()
                                    exitProcess(0)
                                }
                                incomingMessages.send(message)
                            }
                        }
                    } catch (_: EOFException) {
                        exitProcess(-1)
                    } catch (e: Throwable) {
                        logger.error("Error while reading from stdin", e)
                    }
                }

                subtask(context = writer.dispatcher) {
                    val outputStream = ObjectOutputStream(System.out)
                    while (true) {
                        outputStream.writeObject(outgoingMessages.receive())
                        outputStream.flush()
                    }
                }


                object : IsolateContext {
                    override val logger: Logger = logger
                    override val messages: Queue<IsolateMessage> = incomingMessages

                    override suspend fun send(message: IsolateMessage) {
                        outgoingMessages.send(message)
                    }

                    override suspend fun stop() {
                        exitProcess(0)
                    }
                }.apply {
                    log("Starting Isolate (${currentJar.fileName})")
                    instance.run()
                    log("Isolate finished successfully")
                }

            }.getBlocking(10.seconds).getOrThrow()
        } catch (t: Throwable) {
            createLogger().error("Isolate failed ${t.message}", t)
            throw t
        }
    }
}
