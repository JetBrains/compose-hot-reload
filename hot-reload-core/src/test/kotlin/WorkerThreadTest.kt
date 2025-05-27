import org.jetbrains.compose.reload.core.WorkerThread
import org.jetbrains.compose.reload.core.getBlocking
import org.jetbrains.compose.reload.core.reloadMainThread
import org.junit.jupiter.api.Assertions.assertTrue
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
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

@OptIn(ExperimentalAtomicApi::class)
class WorkerThreadTest {

    val thread = WorkerThread("Test")

    @AfterTest
    fun shutdown() {
        thread.shutdown()
    }

    @Test
    fun `test - simple invoke`() {
        val resultInvocations = AtomicInt(0)
        val actionInvocations = AtomicInt(0)

        val result = thread.invoke {
            actionInvocations.incrementAndFetch()
        }.getBlocking(5.seconds)

        assertEquals(1, resultInvocations.load())
        assertEquals(1, actionInvocations.load())
        assertEquals(Result.success(1), result)

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
        assertEquals(Result.success(Unit), future1.getBlocking(5.seconds))
    }

    @Test
    fun `test - shutdown after invoke`() {
        val future1 = thread.invoke { }
        val future2 = thread.invoke { }
        val future3 = thread.shutdown()
        val future4 = thread.invoke { }

        assertTrue(future1.getBlocking(5.seconds).isSuccess)
        assertTrue(future2.getBlocking(5.seconds).isSuccess)
        assertTrue(future3.getBlocking(5.seconds).isSuccess)
        assertTrue(future4.getBlocking(5.seconds).isFailure)
    }

    @Test
    fun `test - exception`() {
        val result = thread.invoke { error("Foo") }.getBlocking(5.seconds)
        assertTrue(result.isFailure)
        assertEquals("Foo", result.exceptionOrNull()?.message)
    }

    @Test
    fun `test - completion handler is invoked in reloadMain`() {
        val invocationThread = AtomicReference<Thread?>(null)
        val result = thread.invoke { }.invokeOnCompletion {
            assertNull(invocationThread.exchange(Thread.currentThread()))
        }

        assertTrue(result.getBlocking(5.seconds).isSuccess)
        assertEquals(reloadMainThread, invocationThread.load())
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

        thread.shutdown().getBlocking(5.seconds)
        values.forEachIndexed { index, value ->
            assertEquals(index, value)
        }
    }
}
