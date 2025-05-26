/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.core

import kotlin.coroutines.Continuation
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext

@InternalHotReloadApi
public class ThreadDispatcher(
    private val workerThread: WorkerThread,
    private val isImmediate: Boolean = false,
) : ContinuationInterceptor {
    override fun <T> interceptContinuation(continuation: Continuation<T>): Continuation<T> {
        return Continuation(continuation.context) { result ->
            if (Thread.currentThread() == workerThread && isImmediate) {
                continuation.resumeWith(result)
            } else {
                workerThread.invoke { continuation.resumeWith(result) }
            }
        }
    }

    override val key: CoroutineContext.Key<*>
        get() = ContinuationInterceptor.Key
}
