import org.jetbrains.compose.reload.core.Broadcast
import org.jetbrains.compose.reload.core.Future
import org.jetbrains.compose.reload.core.WorkerThread
import org.jetbrains.compose.reload.core.collectWhile
import org.jetbrains.compose.reload.core.complete
import org.jetbrains.compose.reload.core.dispatcher
import org.jetbrains.compose.reload.core.getBlocking
import org.jetbrains.compose.reload.core.getOrThrow
import org.jetbrains.compose.reload.core.launchTask
import org.jetbrains.compose.reload.core.reloadMainDispatcherImmediate
import org.jetbrains.compose.reload.core.withThread
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
        val broadcast = Broadcast<Int>()
        val received = mutableListOf<Int>()

        launchTask {
            launch(reloadMainDispatcherImmediate) {
                broadcast.collectWhile { value ->
                    received.add(value)
                    received.size < 5
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
        val broadcast = Broadcast<Int>()
        val collectingThread = Future<Thread>()
        val worker1 = WorkerThread("w1")
        val worker2 = WorkerThread("w2")

        launchTask {
            invokeOnFinish { worker1.shutdown() }
            invokeOnFinish { worker2.shutdown() }

            launch(worker1.dispatcher) {
                broadcast.collectWhile { value ->
                    collectingThread.complete(Thread.currentThread())
                    false
                }
            }

            withThread(worker2) {
                while (!collectingThread.isCompleted()) {
                    broadcast.send(0)
                }
            }
        }.getBlocking()

        assertTrue(collectingThread.isCompleted())
        assertEquals(worker1, collectingThread.getBlocking().getOrThrow())
    }
}
