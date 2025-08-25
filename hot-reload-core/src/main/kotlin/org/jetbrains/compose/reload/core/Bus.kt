/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.core

import org.jetbrains.compose.reload.DelicateHotReloadApi
import java.util.concurrent.atomic.AtomicReference

/**
 * Implementation of [Broadcast] which allows sending events
 */
@DelicateHotReloadApi
public interface Bus<T> : Broadcast<T>, Send<T>

@DelicateHotReloadApi
public fun <T> Bus(): Bus<T> {
    return BusImpl()
}

private class BusImpl<T> : Bus<T> {
    private val dispatchQueues = AtomicReference(listOf<Queue<T>>())

    override suspend fun send(value: T) {
        val dispatchQueues = dispatchQueues.get()
        dispatchQueues.map { queue ->
            launchTask("BusImpl.dispatch($queue)") {
                queue.send(value)
            }
        }
    }

    override suspend fun collect(action: suspend (T) -> Unit) {
        val queue = Queue<T>()
        dispatchQueues.update { it + queue }

        while (isActive()) {
            val element = queue.receive()
            try {
                action(element)
            } catch (_: StopCollectingException) {
                break
            } catch (t: Throwable) {
                throw t
            }
        }
    }
}
