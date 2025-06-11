import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.reload.core.Future
import org.jetbrains.compose.reload.core.StoppedException
import org.jetbrains.compose.reload.core.WorkerThread
import org.jetbrains.compose.reload.core.awaitOrThrow
import org.jetbrains.compose.reload.core.complete
import org.jetbrains.compose.reload.core.dispatcher
import org.jetbrains.compose.reload.core.exception
import org.jetbrains.compose.reload.core.getBlocking
import org.jetbrains.compose.reload.core.getOrThrow
import org.jetbrains.compose.reload.core.invokeOnFinish
import org.jetbrains.compose.reload.core.invokeOnStop
import org.jetbrains.compose.reload.core.isFailure
import org.jetbrains.compose.reload.core.launchTask
import org.jetbrains.compose.reload.core.reloadMainThread
import org.jetbrains.compose.reload.core.suspendStoppableCoroutine
import org.jetbrains.compose.reload.core.withThread
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.suspendCoroutine
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.seconds

/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

class StopTests {

    private val resources = mutableListOf<AutoCloseable>()
    fun <T : AutoCloseable> use(value: T): T = synchronized(resources) {
        resources.add(value)
        value
    }

    @AfterTest
    fun close() {
        synchronized(resources) {
            resources.forEach { it.close() }
            resources.clear()
        }
    }

    @Test
    fun `test - simple stop`() {
        val isSuspended = Future()

        /* Stoppable suspended task */
        val task = launchTask {
            suspendStoppableCoroutine<Nothing> {
                isSuspended.complete()
            }.getOrThrow()
        }

        /* Wait for the suspension and then stop the coroutine */
        launchTask {
            isSuspended.awaitOrThrow()
            task.stop()
        }

        val result = task.getBlocking(5.seconds)
        if (!result.isFailure()) fail("Expected task to be failed")
        assertIs<StoppedException>(result.exception)
    }

    @Test
    fun `test - kotlinxCoroutines cancel`() = runTest {
        val isSuspended = Future()

        val task = async<Unit> {
            suspendStoppableCoroutine<Nothing> {
                isSuspended.complete()
            }
        }

        isSuspended.await()
        task.cancel()
        assertFailsWith<CancellationException> { task.await() }
    }


    @Test
    fun `test - stop`() {
        val worker1 = use(WorkerThread("w1"))
        val worker2 = use(WorkerThread("w2"))

        val worker1CancelThread = Future<Thread>()
        val worker2CancelThread = Future<Thread>()

        launchTask {
            withThread(worker1) {
                invokeOnStop {
                    assertTrue(worker1CancelThread.complete(Thread.currentThread()))
                }
            }

            withThread(worker2) {
                invokeOnStop {
                    assertTrue(worker2CancelThread.complete(Thread.currentThread()))
                }
            }

            stop()
        }.getBlocking(5.seconds)

        assertEquals(reloadMainThread, worker1CancelThread.getBlocking(5.seconds).getOrThrow())
        assertEquals(reloadMainThread, worker2CancelThread.getBlocking(5.seconds).getOrThrow())
    }

    @Test
    fun `test - stop child by error`() {
        try {
            val worker1 = use(WorkerThread("w1"))
            val worker2 = use(WorkerThread("w2"))
            val receivedCompletion = Future<Throwable?>()

            launchTask {
                subtask(context = worker1.dispatcher) {
                    invokeOnStop { error ->
                        assertTrue(receivedCompletion.complete(error))
                        assertEquals("Foo", assertNotNull(error).message)
                    }
                    suspendCoroutine<Nothing> { }
                }

                subtask(context = worker2.dispatcher) {
                    assertEquals(worker2, Thread.currentThread())
                    error("Foo")
                }
            }

            val receivedThrowable = receivedCompletion.getBlocking(5.seconds).getOrThrow()
            assertEquals("Foo", receivedThrowable?.message)
        } finally {
            reloadMainThread.invoke { }.getBlocking(5.seconds).getOrThrow()
        }
    }

    @Test
    fun `test - stop is not called if coroutine finished`() {
        val stopCalled = AtomicBoolean(false)
        launchTask {
            subtask {
                invokeOnStop {
                    assertFalse(stopCalled.getAndSet(true))
                }
            }.await()

            stop()
        }.getBlocking(5.seconds)

        assertEquals(false, stopCalled.get())
    }

    @Test
    fun `test - stop is called when waiting for child`() {
        val stopCalled = AtomicBoolean(false)
        val childLaunched = Future<Unit>()

        val testCoroutine = launchTask<Unit> {
            invokeOnStop {
                assertTrue(stopCalled.getAndSet(true))
            }

            subtask {
                childLaunched.complete(Unit)
                stop()
            }
        }

        /* Wait for the child to be launched */
        childLaunched.getBlocking(5.seconds).getOrThrow()

        /* Wait for the coroutine to finish */
        assertFailsWith<StoppedException> { testCoroutine.getBlocking(5.seconds).getOrThrow() }
        assertEquals(true, stopCalled.get())
    }

    @Test
    fun `test - stop invokes the onFinish`() {
        val finishInvocations = AtomicInteger(0)
        launchTask {
            invokeOnFinish {
                assertEquals(0, finishInvocations.andIncrement)
            }
            stop()
        }.getBlocking(5.seconds)

        reloadMainThread.invoke { }.getBlocking(5.seconds)
        assertEquals(1, finishInvocations.get())
    }

}
