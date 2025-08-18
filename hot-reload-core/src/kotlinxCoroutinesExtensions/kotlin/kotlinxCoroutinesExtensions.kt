package org.jetbrains.compose.reload.core

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow


/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

fun <T> State<T>.asFlow(): Flow<T> = flow {
    collect { value -> emit(value) }
}

fun <T> State<T>.asChannel(capacity: Int = Channel.RENDEZVOUS): Channel<T> {
    val channel = Channel<T>(capacity)
    val task = launchTask("State<T>.asChannel") {
        collect { value -> channel.send(value) }
    }
    channel.invokeOnClose { task.stop() }
    return channel
}
