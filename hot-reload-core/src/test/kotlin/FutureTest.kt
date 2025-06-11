import org.jetbrains.compose.reload.core.Future
import org.jetbrains.compose.reload.core.complete
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
}
