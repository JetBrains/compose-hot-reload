import org.jetbrains.compose.reload.core.Future
import org.jetbrains.compose.reload.core.FutureImpl
import org.jetbrains.compose.reload.core.awaitIdle
import org.jetbrains.compose.reload.core.complete
import org.jetbrains.compose.reload.core.getBlocking
import org.jetbrains.compose.reload.core.getOrThrow
import org.jetbrains.compose.reload.core.launchTask
import org.jetbrains.compose.reload.core.reloadMainThread
import org.jetbrains.compose.reload.core.withAsyncTrace
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds

/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

class FutureTest {

    @Test
    fun `test - awaitWith - complete`() {
        val future = Future<Int>()
        val invoked = AtomicReference<Int>(null)
        val continuation = Continuation<Int>(EmptyCoroutineContext) {
            assertNull(invoked.getAndSet(it.getOrThrow()))
        }
        future.awaitWith(continuation)
        future.complete(123)
        assertEquals(123, invoked.get())
    }

    @Test
    fun `test - complete - awaitWith`() {
        val future = Future<Int>()
        future.complete(123)
        val invoked = AtomicReference<Int>(null)

        val continuation = Continuation<Int>(EmptyCoroutineContext) {
            assertNull(invoked.getAndSet(it.getOrThrow()))
        }

        future.awaitWith(continuation)
        assertEquals(123, invoked.get())
    }

    @Test
    fun `test - awaitWith - dispose`() {
        val future = Future<Int>()
        val continuation = Continuation<Int>(EmptyCoroutineContext) {
            error("Should not be invoked")
        }

        future.awaitWith(continuation).dispose()
        future.complete(123)
    }

    @Test
    fun `test - stop`() {
        val future = Future<Int>() as FutureImpl<Int>

        val myTask = launchTask {
            future.await()
        }

        launchTask {
            withAsyncTrace("Check 'myTask' is waiting for the future") {
                reloadMainThread.awaitIdle()
                val current = future.state.get()
                assertEquals(1, assertIs<FutureImpl.State.Waiting<*>>(current).waiting.size)
            }


            withAsyncTrace("Check stopped task is properly cleaned up") {
                myTask.stop()
                reloadMainThread.awaitIdle()
                assertEquals(0, assertIs<FutureImpl.State.Waiting<*>>(future.state.get()).waiting.size)
            }

        }.getBlocking(5.seconds).getOrThrow()
    }
}
