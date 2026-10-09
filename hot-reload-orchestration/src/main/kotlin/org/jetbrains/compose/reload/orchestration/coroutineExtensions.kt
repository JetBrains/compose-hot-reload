/*
 * Copyright 2024-2026 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.orchestration

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.reload.core.Future
import org.jetbrains.compose.reload.core.complete
import org.jetbrains.compose.reload.core.getBlocking
import org.jetbrains.compose.reload.core.getOrThrow
import org.jetbrains.compose.reload.core.invokeOnValue
import org.jetbrains.compose.reload.core.launchTask
import org.jetbrains.compose.reload.core.reloadMainDispatcherImmediate
import org.jetbrains.compose.reload.core.reloadMainThread
import java.lang.ref.WeakReference
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import kotlin.time.Duration

public fun OrchestrationHandle.asBlockingQueue(): BlockingQueue<OrchestrationMessage> {
    val queue = LinkedBlockingQueue<OrchestrationMessage>()
    val queueReference = WeakReference(queue)


    launchTask("asBlockingQueue") {
        messages.collect { message ->
            val queue = queueReference.get()
            if (queue == null) stop()
            else queue.put(message)
        }
    }


    return queue
}

public fun OrchestrationHandle.asFlow(): Flow<OrchestrationMessage> = callbackFlow {
    val producer = this

    val task = launchTask("asFlow") {
        messages.collect { message ->
            send(message)
        }
    }

    launchTask("asFlow.close") {
        this@asFlow.await()
        producer.close()
    }

    awaitClose { task.stop() }
}.buffer(Channel.UNLIMITED)

/**
 * Creates a [ReceiveChannel] that emits all messages from the [OrchestrationHandle].
 * The channel is buffered and closed when the [OrchestrationHandle] is closed.
 *
 * The creation of the channel is performed on the [reloadMainThread] and messages
 * are guaranteed to be delivered once the channel is returned
 */
public fun OrchestrationHandle.asChannel(): ReceiveChannel<OrchestrationMessage> =
    reloadMainThread.invokeImmediateBlocking {
        val channel = Channel<OrchestrationMessage>(Channel.UNLIMITED)

        /*
        Launching the collecting task using the immediate dispatcher.
        This ensures that the 'collect' method is called right away, ensuring
        that no messages will be lost as the underlying listener is registered now
         */
        val task = launchTask("asChannel", context = reloadMainDispatcherImmediate) {
            messages.collect { message ->
                channel.send(message)
            }
        }

        launchTask("asChannel.close") {
            this@asChannel.await()
            channel.close()
        }

        channel.invokeOnClose { task.stop() }
        channel
    }


public fun <T : OrchestrationState?> OrchestrationStates.flowOf(key: OrchestrationStateKey<T>): Flow<T> = flow {
    get(key).collect { value -> emit(value) }
}

/**
 * Sends [request] over orchestration, then collects the resulting messages until [match] maps one
 * to a non-null value, returning it (or null if none arrives within [timeout]).
 *
 * The response channel is opened *before* [request] is sent, eliminating the race where a result
 * could otherwise arrive before a cold flow starts collecting. [consumeAsFlow] auto-closes the
 * channel once a terminal value is found.
 *
 * [match] is invoked on messages in arrival order, so it may accumulate state across calls
 * (e.g. capturing an id from one message to match it against a later one).
 */
public suspend fun <R> OrchestrationHandle.sendAndAwait(
    request: OrchestrationMessage,
    timeout: Duration,
    match: (OrchestrationMessage) -> R?,
): R? {
    val channel = asChannel()
    return channel.consumeAsFlow().let { flow ->
        send(request)
        withTimeoutOrNull(timeout) { flow.mapNotNull(match).firstOrNull() }
    }
}

/**
 * Blocking counterpart of [sendAndAwait] for callers outside the coroutine world (e.g. Gradle task
 * actions): sends [request] and waits for [match] to map an arriving message to a non-null value,
 * or null if none arrives within [timeout]. A failed send is thrown.
 *
 * The listener is registered *before* [request] is sent (so a reply cannot be missed), runs on the
 * orchestration thread, and is removed when the wait ends.
 */
public fun <R> OrchestrationHandle.sendAndAwaitBlocking(
    request: OrchestrationMessage,
    timeout: Duration,
    match: (OrchestrationMessage) -> R?,
): R? {
    val reply = Future<R>()
    val listener = messages.invokeOnValue { message -> match(message)?.let { value -> reply.complete(value) } }
    try {
        sendBlocking(request).getOrThrow()
        return reply.getBlocking(timeout).leftOrNull()
    } finally {
        listener.dispose()
    }
}
