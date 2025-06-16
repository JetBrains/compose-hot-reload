import org.jetbrains.compose.reload.core.Bus
import org.jetbrains.compose.reload.core.Future
import org.jetbrains.compose.reload.core.WorkerThread
import org.jetbrains.compose.reload.core.complete
import org.jetbrains.compose.reload.core.dispatcher
import org.jetbrains.compose.reload.core.getBlocking
import org.jetbrains.compose.reload.core.getOrThrow
import org.jetbrains.compose.reload.core.invokeOnFinish
import org.jetbrains.compose.reload.core.launchTask
import org.jetbrains.compose.reload.core.reloadMainDispatcherImmediate
import org.jetbrains.compose.reload.core.stopCollecting
import org.jetbrains.compose.reload.core.withThread
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

class BroadcastTest {
    @Test
    fun `test - send and receive`() {
        val broadcast = Bus<Int>()
        val received = mutableListOf<Int>()

        launchTask {
            subtask(context = reloadMainDispatcherImmediate) {
                broadcast.collect { value ->
                    received.add(value)
                    if (received.size >= 5) stopCollecting()
                }
            }

            repeat(128) { value ->
                broadcast.send(value)
            }

        }.getBlocking(5.seconds)

        assertEquals(mutableListOf(0, 1, 2, 3, 4), received)
    }

    @Test
    fun `test - threads`() {
        val broadcast = Bus<Int>()
        val collectingThread = Future<Thread>()
        val worker1 = WorkerThread("w1")
        val worker2 = WorkerThread("w2")


        val task = launchTask {
            invokeOnFinish { worker1.shutdown() }
            invokeOnFinish { worker2.shutdown() }

            subtask(context = worker1.dispatcher) {
                broadcast.collect { value ->
                    collectingThread.complete(Thread.currentThread())
                    stopCollecting()
                }
            }

            withThread(worker2) {
                val value = AtomicInteger(0)
                while (!collectingThread.isCompleted()) {
                    broadcast.send(value.andIncrement)
                }
            }

        }
        task.getBlocking(5.seconds)
        task.getBlocking(5.seconds).getOrThrow()

        assertTrue(collectingThread.isCompleted())
        assertEquals(worker1, collectingThread.getBlocking(5.seconds).getOrThrow())
    }
}
