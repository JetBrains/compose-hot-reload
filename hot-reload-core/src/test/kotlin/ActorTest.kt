import org.jetbrains.compose.reload.core.Actor
import org.jetbrains.compose.reload.core.ActorClosedException
import org.jetbrains.compose.reload.core.awaitOrThrow
import org.jetbrains.compose.reload.core.getBlocking
import org.jetbrains.compose.reload.core.getOrThrow
import org.jetbrains.compose.reload.core.launchTask
import org.junit.jupiter.api.Assertions.assertFalse
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

class ActorTest {

    @Test
    fun `test - send - receive`() {
        val actor = Actor<String, Int>()

        launchTask("actor") {
            actor.process { value -> value.toInt() }
        }

        val result = launchTask("sender") {
            listOf(actor.invoke("0"), actor.invoke("1"), actor.invoke("2"))
        }.value.getBlocking(5.seconds).getOrThrow()

        assertEquals(listOf(0, 1, 2), result)
    }

    @Test
    fun `test - exception in processor`() {
        val actor = Actor<String, Int>()
        launchTask("actor") { actor.process { value -> value.toInt() } }

        val result = launchTask("sender") {
            listOf(actor.invoke("0"), actor.invoke("Not a Number"))
        }.value.getBlocking(5.seconds)

        assertFailsWith<IllegalArgumentException> { result.getOrThrow() }
        assertTrue(actor.isClosed())

        assertFailsWith<ActorClosedException> {
            launchTask("sender 2") {
                actor.invoke("1")
            }.value.getBlocking().getOrThrow()
        }
    }

    @Test
    fun `test - exception in processor - 2`() {
        val actor = Actor<String, Int>()
        launchTask("actor1") { actor.process { value -> value.toInt() } }
        launchTask("actor2") { actor.process { value -> -1 } }

        /* Should be processed successfully by the first processor `*/
        assertEquals(1, launchTask("sender1") {
            actor.invoke("1")
        }.value.getBlocking(5.seconds).getOrThrow())

        /* Should fail the first processor, starting the second processor */
        assertFailsWith<IllegalArgumentException> {
            launchTask("sender2") { actor.invoke("Foo") }.getBlocking(5.seconds).getOrThrow()
        }
    }

    @Test
    fun `test - close`(): Unit = launchTask("test") {
        val actor = Actor<String, Int>()
        val processor = launchTask("actor") { actor.process { value -> value.toInt() } }
        assertEquals(0, actor.invoke("0"))
        processor.stop()
        Unit
    }.value.getBlocking(5.seconds).getOrThrow()

    @Test
    fun `test - close in processor`() = launchTask {
        val actor = Actor<Int, Int>()

        launchTask {
            actor.process { value ->
                if (value == Int.MIN_VALUE) actor.close()
                value
            }
        }

        assertEquals(1, actor(1))
        assertEquals(Int.MIN_VALUE, actor(Int.MIN_VALUE))
        assertFailsWith<ActorClosedException> { actor(2) }
        Unit
    }.getBlocking(5.seconds).getOrThrow()

    @Test
    fun `test - close unblocks processors`() = launchTask {
        val actor = Actor<Int, Int>()
        val finished = AtomicBoolean(false)
        val task = subtask {
            actor.process { it }
            assertFalse(finished.getAndSet(true))
        }


        subtask {
            assertEquals(false, finished.get())
            actor.close()
        }

        task.awaitOrThrow()
        assertTrue(finished.get())
    }.getBlocking(5.seconds).getOrThrow()
}
