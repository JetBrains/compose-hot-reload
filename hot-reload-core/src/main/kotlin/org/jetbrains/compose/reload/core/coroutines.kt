/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.core

import org.jetbrains.compose.reload.InternalHotReloadApi
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.createCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException


@InternalHotReloadApi
public suspend fun <T> withThread(
    workerThread: WorkerThread, isImmediate: Boolean = false, action: suspend () -> T
): T {
    if (Thread.currentThread() == workerThread && isImmediate) return action()
    val newContext = coroutineContext + WorkerThreadDispatcher(workerThread, isImmediate)
    return suspendStoppableCoroutine { continuation ->
        action.createCoroutine(Continuation(newContext) { result -> continuation.resumeWith(result) })
            .resume(Unit)
    }.getOrThrow()
}

public suspend fun WorkerThread.awaitIdle(): Unit = invokeWhenIdle {}.await().getOrThrow()

public suspend fun <T> Future<T>.awaitOrThrow(): T = await().getOrThrow()

public suspend fun <T> suspendStoppableCoroutine(action: (Continuation<T>) -> Unit): Try<T> {
    val task = coroutineContext[Task]
    var onStop: Disposable? = null
    val asyncTraces = coroutineContext.createAsyncTraces()
    return try {
        suspendCancellableCoroutine { continuation ->
            val select = SelectContinuation(continuation.context + asyncTraces, continuation)
            onStop = task?.invokeOnStop { error -> select.resumeWithException(error) }
            action(select)
        }.toLeft()
    } catch (t: Throwable) {
        if (t is CancellationException) {
            task?.stop(t)
        }
        t.addSuppressed(AsyncTracesThrowable(asyncTraces))
        t.toRight()
    } finally {
        onStop?.dispose()
    }
}

/**
 * Continuation which allows multiple calls to [Continuation.resumeWith]. Only
 * the first call to [resumeWith] will be forwarded to the underlying [continuation]
 */
internal class SelectContinuation<T>(
    override val context: CoroutineContext,
    private val continuation: Continuation<T>
) : Continuation<T> {
    private val isResumed = AtomicBoolean(false)
    override fun resumeWith(result: Result<T>) {
        if (isResumed.compareAndSet(false, true)) {
            continuation.resumeWith(result)
        }
    }
}

@Suppress("RedundantSuspendModifier")
public suspend fun stopNow(): Nothing = throw StoppedException()

public suspend fun currentTask(): Task<*> =
    coroutineContext[Task] ?: error("Missing '${Task::class.simpleName}' in context")

public suspend fun stop(error: Throwable? = null) {
    coroutineContext[Task]?.stop(error)
}

public suspend fun invokeOnStop(action: (error: Throwable?) -> Unit): Disposable {
    return currentTask().invokeOnStop(action)
}

public suspend fun invokeOnFinish(action: (result: Try<*>) -> Unit): Disposable {
    return currentTask().invokeOnFinish(action)
}

public suspend fun launchOnFinish(action: suspend Task<*>.(result: Try<*>) -> Unit): Disposable {
    val task = currentTask()
    return task.launchOnFinish("${task.name}.launchOnFinish", action)
}

public suspend fun launchOnStop(action: suspend Task<*>.(error: Throwable) -> Unit): Disposable {
    val task = currentTask()
    return currentTask().launchOnStop("${task.name}.launchOnStop", action)
}

public suspend fun isActive(): Boolean = coroutineContext.isActive()

public suspend fun CoroutineContext.isActive(): Boolean = this[Task]?.isActive() ?: hasActiveJob()

public fun <T> Continuation<T>.resumeWith(result: Try<T>) {
    resumeWith(result.toResult())
}


public class StoppedException(override val cause: Throwable? = null) : CancellationException()
