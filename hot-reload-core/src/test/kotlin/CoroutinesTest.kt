import org.jetbrains.compose.reload.core.Future
import org.jetbrains.compose.reload.core.WorkerThread
import org.jetbrains.compose.reload.core.await
import org.jetbrains.compose.reload.core.complete
import org.jetbrains.compose.reload.core.dispatcher
import org.jetbrains.compose.reload.core.getBlocking
import org.jetbrains.compose.reload.core.globalLaunch
import org.jetbrains.compose.reload.core.newTask
import org.jetbrains.compose.reload.core.reloadMainThread
import org.jetbrains.compose.reload.core.withThread
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.suspendCoroutine
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

@OptIn(ExperimentalAtomicApi::class)
class CoroutinesTest {

    private val resources = mutableListOf<AutoCloseable>()
    fun <T : AutoCloseable> use(value: T): T = synchronized(resources) {
        resources.add(value)
        value
    }

    @AfterTest
    fun cleanup() {
        synchronized(resources) {
            resources.forEach { it.close() }
            resources.clear()
        }
    }

    @Test
    fun `test - launch`() {
        val future = globalLaunch {
            assertTrue(Thread.currentThread() == reloadMainThread, "Expected 'reloadMainThread'")
            42
        }

        assertEquals(42, future.getBlocking(5.seconds).getOrThrow())
    }

    @Test
    fun `test - switching threads`() {
        val threads = globalLaunch {
            val threads = mutableListOf<Thread>()
            threads += Thread.currentThread()
            val worker1 = use(WorkerThread("w1"))
            val worker2 = use(WorkerThread("w2"))
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
        val future: Future<Int> = globalLaunch {
            withThread(use(WorkerThread("w1"))) {
                error("Foo")
            }
        }

        assertTrue(future.getBlocking(5.seconds).isFailure)
        assertEquals("Foo", future.getBlocking(5.seconds).exceptionOrNull()?.message)
    }

    @Test
    fun `test - stop`() {
        val worker1 = use(WorkerThread("w1"))
        val worker2 = use(WorkerThread("w2"))

        val worker1CancelThread = Future<Thread>()
        val worker2CancelThread = Future<Thread>()

        globalLaunch {
            newTask {
                withThread(worker1) {
                    invokeOnStop {
                        assertTrue(worker1CancelThread.completeWith(Result.success(Thread.currentThread())))
                    }
                }

                withThread(worker2) {
                    invokeOnStop {
                        assertTrue(worker2CancelThread.completeWith(Result.success(Thread.currentThread())))
                    }
                }

                stop()
            }
        }.getBlocking(5.seconds)

        assertEquals(reloadMainThread, worker1CancelThread.getBlocking(5.seconds).getOrThrow())
        assertEquals(reloadMainThread, worker2CancelThread.getBlocking(5.seconds).getOrThrow())
    }

    @Test
    fun `test - stop child by error`() {
        val worker1 = use(WorkerThread("w1"))
        val worker2 = use(WorkerThread("w2"))
        val receivedCompletion = Future<Throwable?>()

        globalLaunch {
            launch(worker1.dispatcher) {
                invokeOnStop { error ->
                    assertTrue(receivedCompletion.complete(error))
                    assertEquals("Foo", assertNotNull(error).message)
                }
                suspendCoroutine<Nothing> { }
            }

            launch(worker2.dispatcher) {
                assertEquals(worker2, Thread.currentThread())
                error("Foo")
            }
        }

        assertEquals("Foo", receivedCompletion.getBlocking(5.seconds).getOrThrow()?.message)
    }

    @Test
    fun `test - stop is not called if coroutine finished`() {
        val stopCalled = AtomicBoolean(false)
        globalLaunch {
            launch {
                invokeOnStop {
                    assertFalse(stopCalled.exchange(true))
                }
            }.await()

            stop()
        }.getBlocking(5.seconds)

        assertEquals(false, stopCalled.load())
    }
}
