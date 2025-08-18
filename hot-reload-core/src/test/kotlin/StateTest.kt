import org.jetbrains.compose.reload.core.Future
import org.jetbrains.compose.reload.core.MutableState
import org.jetbrains.compose.reload.core.Queue
import org.jetbrains.compose.reload.core.await
import org.jetbrains.compose.reload.core.awaitIdle
import org.jetbrains.compose.reload.core.complete
import org.jetbrains.compose.reload.core.getBlocking
import org.jetbrains.compose.reload.core.getOrThrow
import org.jetbrains.compose.reload.core.isActive
import org.jetbrains.compose.reload.core.launchTask
import org.jetbrains.compose.reload.core.reloadMainThread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

class StateTest {
    @Test
    fun `test - simple state`() {
        val state = MutableState(0)
        assertEquals(0, state.value)

        state.update { it + 1 }
        assertEquals(1, state.value)

        state.update { 5 }
        assertEquals(5, state.value)
    }

    @Test
    fun `test - compareAndSet`() {
        val state = MutableState(0)
        state.compareAndSet(0, 1)
        assertEquals(1, state.value)

        state.compareAndSet(0, 2)
        assertEquals(1, state.value)

        state.compareAndSet(1, 3)
        assertEquals(3, state.value)
    }

    @Test
    fun `test - compareAndSet - nullable`() {
        val state = MutableState<Int?>(null)
        state.compareAndSet(null, 1)
        assertEquals(1, state.value)

        state.compareAndSet(1, null)
        assertEquals(null, state.value)

        state.compareAndSet(10, 12)
        assertEquals(null, state.value)

        state.compareAndSet(null, 10)
        assertEquals(10, state.value)

        state.compareAndSet(10, 12)
        assertEquals(12, state.value)
    }

    @Test
    fun `test - collecting state`() {
        val state = MutableState(0)
        state.update { it + 1 }

        val received = Queue<Int>()

        val receiver = launchTask("receiver") {
            state.collect {
                received.send(it)
            }
        }

        launchTask("sender") {
            /* Check the initial value received */
            assertEquals(1, received.receive())

            /* Update and check if the new value is received */
            state.update { 2 }
            assertEquals(2, received.receive())

            /* Update and check if the new value is received */
            state.update { 10 }
            assertEquals(10, received.receive())

            receiver.stop()
        }.getBlocking(5.seconds).getOrThrow()
    }

    @Test
    fun `test - empty state`() {
        val state = MutableState<Int?>(null)
        val received = Queue<Int?>()

        val receiver = launchTask("receiver") {
            state.collect { received.add(it) }
        }

        launchTask("controller") {
            assertEquals(null, received.receive())
            receiver.stop()
        }.getBlocking(5.seconds).getOrThrow()
    }

    @Test
    fun `test - conflation`() {
        val state = MutableState(0)

        val received = Queue<Int>()
        var blockReceiver: Future<Unit>? = null

        val receiver = launchTask("receiver") {
            state.collect {
                received.send(it)
                blockReceiver?.await()
            }
        }

        launchTask("sender") {
            /* Check the initial value received */
            blockReceiver = Future()
            assertEquals(0, received.receive())

            state.update { 1 }
            state.update { 2 }
            state.update { 3 }
            state.update { 4 }
            state.update { 5 }
            blockReceiver.complete(Unit)

            assertEquals(5, received.receive())
            receiver.stop()
        }.getBlocking(5.seconds).getOrThrow()
    }

    @Test
    fun `test - await`() {
        val state = MutableState(0)

        val result = launchTask {
            state.await { it >= 10 }
        }

        launchTask {
            reloadMainThread.awaitIdle()
            while (result.isActive()) {
                state.update { it + 1 }
            }
        }

        assertEquals(10, result.getBlocking(5.seconds).getOrThrow())
    }
}
