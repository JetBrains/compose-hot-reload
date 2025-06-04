/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

@file:OptIn(ExperimentalAtomicApi::class)

package org.jetbrains.compose.reload.core

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.withLock
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume

public class QueueClosedException(
    override val cause: Throwable?
) : Exception("Queue closed")

public interface SendQueue<T> {
    public suspend fun send(value: T)
}

public interface ReceiveQueue<T> {
    public suspend fun receive(): T
    public fun nextOrNothing(): Either<T, Nothing?>
}

public interface Queue<T> : SendQueue<T>, ReceiveQueue<T>

public fun <T> Queue(): Queue<T> {
    return QueueImpl()
}

private class QueueImpl<T> : Queue<T> {
    private val senders = ArrayDeque<SuspendedSender<T>>()
    private val receivers = ArrayDeque<SuspendedReceiver<T>>()
    private val lock = ReentrantLock()

    override suspend fun send(value: T) {
        val receiver = lock.withLock { receivers.removeFirstOrNull() }

        /* Fast path: If we have found a receiver, we can resume it right, without suspending the sender */
        if (receiver != null) {
            receiver.continuation.resume(value)
            return
        }

        /* No sender was immediately available, we suspend */
        suspendStoppableCoroutine { continuation ->
            val receiver = lock.withLock {
                /* Double Check: Until this point a new receiver could be available! */
                if (receivers.isNotEmpty()) {
                    return@withLock receivers.removeFirst()
                }

                /* There was really no receiver: We can suspend the sender */
                senders.addLast(SuspendedSender(continuation, value))
                return@suspendStoppableCoroutine
            }

            /* We can only reach this point if a receiver was indeed available */
            receiver.continuation.resume(value)
        }
    }

    override suspend fun receive(): T {
        val sender = lock.withLock { senders.removeFirstOrNull() }
        /* Fast path: We have a suspended sender: Let's take the value from there */
        if (sender != null) {
            sender.continuation.resume(Unit)
            return sender.value
        }

        return suspendStoppableCoroutine <T> { continuation ->
            val senderOrNull = lock.withLock {
                /* Double Check: Until this point a new sender could be available! */
                if (senders.isNotEmpty()) {
                    return@withLock senders.removeFirst()
                }

                receivers.addLast(SuspendedReceiver(continuation))
                null
            }

            senderOrNull?.continuation?.resume(Unit)
        }
    }

    override fun nextOrNothing(): Either<T, Nothing?> {
        val sender = lock.withLock { senders.removeFirstOrNull() }
        if (sender != null) {
            sender.continuation.resume(Unit)
            return sender.value.toLeft()
        }

        return null.toRight()
    }

    private class SuspendedSender<T>(
        val continuation: Continuation<Unit>, val value: T
    )

    private class SuspendedReceiver<T>(
        val continuation: Continuation<T>
    )
}
