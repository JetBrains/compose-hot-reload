import org.jetbrains.compose.reload.core.Queue
import org.jetbrains.compose.reload.core.getBlocking
import org.jetbrains.compose.reload.core.launchTask
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

class QueueTest {
    @Test
    fun `test - simple send and receive`() {
        val events = mutableListOf<String>()

        launchTask {
            val queue = Queue<Int>()
            subtask {
                repeat(6) {
                    val value = queue.receive()
                    events.add("received $value")
                }
            }

            subtask {
                repeat(6) {
                    events.add("sending $it")
                    queue.send(it)
                }
            }
        }.getBlocking(5.seconds)

        assertEquals(
            listOf(
                "sending 0", "sending 1", "received 0", "received 1",
                "sending 2", "sending 3", "received 2", "received 3",
                "sending 4", "sending 5", "received 4", "received 5"
            ), events
        )
    }
}
