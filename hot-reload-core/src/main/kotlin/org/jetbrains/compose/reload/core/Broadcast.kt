/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.core

import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.createCoroutine
import kotlin.coroutines.resume

public interface Broadcast<T> {
    public suspend fun collect(action: suspend (T) -> Unit)
}

public fun <T> Broadcast<T>.invokeOnValue(acton: (T) -> Unit): Disposable {
    val task = launchTask("Broadcast.invokeOnValue") {
        collect { value -> acton(value) }
    }

    return Disposable {
        task.stop()
    }
}

public interface Bus<T> : Broadcast<T>, Send<T>


public fun <T> Bus(): Bus<T> {
    return BusImpl()
}

private class BusImpl<T> : Bus<T> {

    private val dispatchQueues = AtomicReference(listOf<Queue<Dispatch<T>>>())

    override suspend fun send(value: T) {
        if (coroutineContext[Dispatch] != null) {
            error("Cannot call send from within a collect block")
        }

        val dispatchQueues = dispatchQueues.get()
        dispatchQueues.map { queue ->
            launchTask("BusImpl.dispatch($queue)") {
                val dispatch = Dispatch(value)
                queue.send(dispatch)
                dispatch.future.await()
            }
        }
    }

    override suspend fun collect(action: suspend (T) -> Unit) {
        val queue = Queue<Dispatch<T>>()
        dispatchQueues.update { it + queue }

        launchOnFinish {
            dispatchQueues.update { it - queue }
            while (isActive()) {
                queue.receive().future.complete(Unit)
            }
        }

        while (isActive()) {
            val dispatch = queue.receive()
            try {
                val result = suspendStoppableCoroutine { continuation ->
                    action.createCoroutine(dispatch.element, Continuation(continuation.context + dispatch) { result ->
                        continuation.resumeWith(result)
                    }).resume(Unit)
                }.getOrThrow()
                dispatch.future.complete(result)
            } catch (_: StopCollectingException) {
                dispatch.future.complete(Unit)
                break
            } catch (t: Throwable) {
                dispatch.future.completeExceptionally(t)
                throw t
            }
        }
    }

    private class Dispatch<T>(val element: T, val future: CompletableFuture<Unit> = Future()) :
        CoroutineContext.Element {
        override val key: CoroutineContext.Key<*>
            get() = Dispatch

        companion object : CoroutineContext.Key<Dispatch<Any>>
    }
}


public inline fun <reified T> Broadcast<*>.withType(): Broadcast<T> {
    return object : Broadcast<T> {
        override suspend fun collect(action: suspend (T) -> Unit) {
            this@withType.collect {
                if (it is T) action(it)
            }
        }
    }
}
