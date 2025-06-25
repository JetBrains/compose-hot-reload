import org.jetbrains.compose.reload.core.Future
import org.jetbrains.compose.reload.core.WorkerThread
import org.jetbrains.compose.reload.core.complete
import org.jetbrains.compose.reload.core.exceptionOrNull
import org.jetbrains.compose.reload.core.getBlocking
import org.jetbrains.compose.reload.core.getOrThrow
import org.jetbrains.compose.reload.core.invokeOnCompletion
import org.jetbrains.compose.reload.core.isFailure
import org.jetbrains.compose.reload.core.isSuccess
import org.jetbrains.compose.reload.core.reloadMainThread
import org.jetbrains.compose.reload.core.toLeft
import org.jetbrains.compose.reload.core.update
import org.junit.jupiter.api.Assertions.assertTrue
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.time.Duration.Companion.seconds

/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

class WorkerThreadTest {

    val thread = WorkerThread("Worker Thread (under test)")

    @AfterTest
    fun shutdown() {
        thread.shutdown()
    }

    @Test
    fun `test - simple invoke`() {
        val actionInvocations = AtomicInteger(0)

        val result = thread.invoke {
            actionInvocations.incrementAndGet()
        }.getBlocking(5.seconds)

        assertEquals(1, actionInvocations.get())
        assertEquals(1.toLeft(), result)

        thread.shutdown()
    }

    @Test
    fun `test - invoke after shutdown`() {
        thread.shutdown()

        assertFailsWith<RejectedExecutionException> {
            thread.invoke { /* Nothing */ }.getBlocking(5.seconds).getOrThrow()
        }
    }


    @Test
    fun `test - shutdown twice`() {
        val future1 = thread.shutdown()
        val future2 = thread.shutdown()
        assertSame(future1, future2)
        assertEquals(Unit.toLeft(), future1.getBlocking(5.seconds))
    }

    @Test
    fun `test - shutdown after invoke`() {
        val future1 = thread.invoke { }
        val future2 = thread.invoke { }
        val future3 = thread.shutdown()
        val future4 = thread.invoke { }

        future1.getBlocking(5.seconds).getOrThrow()
        future2.getBlocking(5.seconds).getOrThrow()
        future3.getBlocking(5.seconds).getOrThrow()
        assertTrue(future4.getBlocking(5.seconds).isFailure())
    }

    @Test
    fun `test - exception`() {
        val result = thread.invoke { error("Foo") }.getBlocking(5.seconds)
        assertTrue(result.isFailure())
        assertEquals("Foo", result.exceptionOrNull()?.message)
    }

    @Test
    fun `test - completion handler is invoked in reloadMain`() {
        val invocationThread = AtomicReference<Thread?>(null)
        thread.invoke { }.invokeOnCompletion {
            assertNull(invocationThread.getAndSet(Thread.currentThread()))
        }
        assertTrue(thread.invoke { }.getBlocking(5.seconds).isSuccess())
        assertEquals(reloadMainThread, invocationThread.get())
    }

    @Test
    fun `test - invokeWhenIdle`() {
        val events = AtomicReference<List<String>>(emptyList())
        val unleashThread = Future<Unit>()
        thread.invoke { unleashThread.getBlocking() }
        thread.invoke { events.update { it + "1" } }
        thread.invokeWhenIdle { events.update { it + "A" } }
        thread.invoke { events.update { it + "2" } }
        thread.invoke { events.update { it + "3" } }
        thread.invokeWhenIdle { events.update { it + "B" } }

        unleashThread.complete()
        thread.shutdown().getBlocking(5.seconds).getOrThrow()

        assertEquals(listOf("1", "2", "3", "A", "B"), events.get())
    }

    @Test
    fun `stress test`() {
        val submissions = 1024 * 8
        val submittingThreads = 12

        val pool = Executors.newFixedThreadPool(submittingThreads)
        var counter = 0
        val values = intArrayOf(submissions)
        repeat(submissions) { invocation ->
            pool.submit {
                thread.invoke {
                    values[counter] = counter
                    counter++
                }
            }
        }

        pool.shutdown()
        pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)
        thread.shutdown().getBlocking(5.seconds).getOrThrow()

        values.forEachIndexed { index, value ->
            assertEquals(index, value)
        }
    }
}
