/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.orchestration

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import org.jetbrains.compose.reload.core.launchTask
import java.lang.ref.WeakReference
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue

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

public fun OrchestrationHandle.asChannel(): ReceiveChannel<OrchestrationMessage> {
    val channel = Channel<OrchestrationMessage>(Channel.UNLIMITED)

    val task = launchTask("asChannel") {
        messages.collect { message ->
            channel.send(message)
        }
    }

    launchTask("asChannel.close") {
        this@asChannel.await()
        channel.close()
    }

    channel.invokeOnClose { task.stop() }
    return channel
}
