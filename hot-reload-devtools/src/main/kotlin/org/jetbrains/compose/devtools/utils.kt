/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.devtools

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import org.jetbrains.compose.reload.core.Disposable
import org.jetbrains.compose.reload.core.invokeOnStop

@OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
internal fun <T> Flow<T>.conflateAsList(): Flow<List<T>> {
    return flow<List<T>> {
        coroutineScope {
            val queue = Channel<T>(Channel.UNLIMITED)
            launch {
                collect { value -> queue.send(value) }
                queue.close()
            }

            while (!queue.isClosedForReceive) {
                val buffer = buildList {
                    while (true) {
                        add(queue.tryReceive().getOrNull() ?: break)
                    }
                }

                emit(buffer.ifEmpty { listOf(queue.receive()) })
            }
        }
    }
}

internal suspend fun <T> withDisposableShutdownHook(disposable: Disposable, action: suspend () -> T): T {
    val shutdownHook = invokeOnShutdown { disposable.dispose() }
    return try {
        action()
    } finally {
        shutdownHook.dispose()
    }
}

internal suspend fun <T> useDisposableStoppable(disposable: Disposable, action: suspend () -> T): T {
    val stop = invokeOnStop { disposable.dispose() }
    return try {
        action()
    } finally {
        disposable.dispose()
        stop.dispose()
    }
}
