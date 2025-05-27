/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.core

import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.createCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine


@InternalHotReloadApi
public fun <T> launch(context: CoroutineContext = EmptyCoroutineContext, action: suspend () -> T): Future<T> {
    val future = Future<T>()
    val continuation = Continuation(ThreadDispatcher(reloadMainThread) + context) { result ->
        future.completeWith(result)
    }
    action.createCoroutine(continuation).resume(Unit)
    return future
}

public fun <T> launch(workerThread: WorkerThread, action: suspend () -> T): Future<T> {
    return launch(context = ThreadDispatcher(workerThread)) { action() }
}

@InternalHotReloadApi
public suspend fun <T> withThread(
    workerThread: WorkerThread, isImmediate: Boolean = false, action: suspend () -> T
): T {
    val newContext = coroutineContext + ThreadDispatcher(workerThread, isImmediate)
    return suspendCoroutine { continuation ->
        action.createCoroutine(Continuation(newContext) { result -> continuation.resumeWith(result) })
            .resume(Unit)
    }
}

@InternalHotReloadApi
public suspend fun <T> Future<T>.await(): T = suspendCoroutine { continuation ->
    invokeOnCompletion { result -> continuation.resumeWith(result) }
}
