import org.jetbrains.compose.reload.core.Future
import org.jetbrains.compose.reload.core.WorkerThread
import org.jetbrains.compose.reload.core.await
import org.jetbrains.compose.reload.core.getBlocking
import org.jetbrains.compose.reload.core.launch
import org.jetbrains.compose.reload.core.reloadMainThread
import org.jetbrains.compose.reload.core.withThread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

class CoroutinesTest {
    @Test
    fun `test - launch`() {
        val future = launch {
            assertTrue(Thread.currentThread() == reloadMainThread, "Expected 'reloadMainThread'")
            42
        }

        assertEquals(42, future.getBlocking(5.seconds).getOrThrow())
    }

    @Test
    fun `test - switching threads`() {
        val threads = launch {
            val threads = mutableListOf<Thread>()
            threads += Thread.currentThread()
            val worker1 = WorkerThread("w1")
            val worker2 = WorkerThread("w2")
            try {
                withThread(worker1) {
                    threads += Thread.currentThread()
                    withThread(worker2) {
                        threads += Thread.currentThread()
                    }
                }

                threads += Thread.currentThread()
                threads
            } finally {
                worker1.shutdown().await()
                worker2.shutdown().await()
            }
        }

        assertEquals(
            listOf(reloadMainThread.name, "w1", "w2", reloadMainThread.name),
            threads.getBlocking(5.seconds).getOrThrow().map { it.name }
        )
    }

    @Test
    fun `test - error`() {
        val future: Future<Int> = launch {
            val worker1 = WorkerThread("w1")
            withThread(worker1) {
                error("Foo")
            }
        }

        assertTrue(future.getBlocking(5.seconds).isFailure)
        assertEquals("Foo", future.getBlocking(5.seconds).exceptionOrNull()?.message)
    }
}
