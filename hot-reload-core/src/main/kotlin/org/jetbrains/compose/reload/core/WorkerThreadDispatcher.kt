/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.core

import org.jetbrains.compose.reload.InternalHotReloadApi
import java.util.concurrent.RejectedExecutionException
import kotlin.coroutines.Continuation
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resumeWithException

@InternalHotReloadApi
public class WorkerThreadDispatcher(
    private val workerThread: WorkerThread,
    private val isImmediate: Boolean = false,
) : ContinuationInterceptor {
    override fun <T> interceptContinuation(continuation: Continuation<T>): Continuation<T> {
        return Continuation(continuation.context) { result ->
            /* Fast path: We can invoke immediately */
            if (Thread.currentThread() == workerThread && isImmediate) {
                continuation.resumeWith(result)
                return@Continuation
            }


            /* We dispatch to the worker thread */
            val future = workerThread.invoke {
                continuation.resumeWith(result)
            }
            val exception = future.getOrNull()?.exceptionOrNull()
            if (exception is RejectedExecutionException) {
                continuation.resumeWithException(exception)
            }
        }
    }

    override val key: CoroutineContext.Key<*>
        get() = ContinuationInterceptor.Key
}

@InternalHotReloadApi
public val WorkerThread.dispatcher: ContinuationInterceptor
    get() = WorkerThreadDispatcher(this)

@InternalHotReloadApi
public val WorkerThread.dispatcherImmediate: ContinuationInterceptor
    get() = WorkerThreadDispatcher(this, isImmediate = true)
