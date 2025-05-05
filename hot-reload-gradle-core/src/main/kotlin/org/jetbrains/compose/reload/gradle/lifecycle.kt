/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.gradle

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.provideDelegate
import kotlin.coroutines.Continuation
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.createCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@InternalHotReloadGradleApi
fun interface Future<T> {
    suspend fun await(): T
}

@InternalHotReloadGradleApi
fun <T> Future(): CompletableFuture<T> {
    return CompletableFuture()
}

@InternalHotReloadGradleApi
class CompletableFuture<T> : Future<T> {
    private var result: Result<T>? = null
    private val continuations = mutableListOf<Continuation<T>>()

    fun complete(value: T) {
        if (result != null) {
            error("Future is already completed")
        }
        result = Result.success(value)
        invokeContinuations()
    }

    fun completeExceptionally(t: Throwable) {
        if (result != null) {
            error("Future is already completed")
        }
        result = Result.failure(t)
        invokeContinuations()
    }

    override suspend fun await(): T {
        val result = result
        return if (result != null) result.getOrThrow()
        else suspendCoroutine { continuation ->
            continuations.add(continuation)
        }
    }

    private fun invokeContinuations() {
        val result = this.result ?: error("Future is not completed yet")
        val continuations = continuations.toList()
        this.continuations.clear()
        continuations.forEach { continuation ->
            continuation.resumeWith(result)
        }
    }
}

@InternalHotReloadGradleApi
fun <T, R> Future<T>.map(mapper: (T) -> R): Future<R> = Future {
    mapper(await())
}

@InternalHotReloadGradleApi
fun <T, R> Future<T>.flatMap(mapper: (T) -> Future<R>) = Future {
    mapper(await()).await()
}

@InternalHotReloadGradleApi
suspend inline fun <reified T> Future<T>.toProvider(): Provider<T> {
    val property = currentProject().objects.property(T::class.java)
    property.set(await())
    property.disallowChanges()
    return property
}

@InternalHotReloadGradleApi
val Project.lifecycle: Lifecycle by lazyProjectProperty { Lifecycle(this) }

@InternalHotReloadGradleApi
fun Project.launch(action: suspend () -> Unit) = lifecycle.launch(action)

@InternalHotReloadGradleApi
fun <T> future(action: suspend Project.() -> T) = lazyProjectProperty {
    future { action() }
}

@InternalHotReloadGradleApi
suspend fun currentProject(): Project {
    val lifecycle = coroutineContext[Lifecycle] ?: error("Project is not initialized yet")
    return lifecycle.project
}

internal fun <T> Project.future(action: suspend () -> T): Future<T> {
    val future = Future<T>()
    launch {
        val result = runCatching { action() }
        result.fold(
            onSuccess = { future.complete(it) },
            onFailure = { future.completeExceptionally(it) }
        )
    }
    return future
}

@InternalHotReloadGradleApi
class Lifecycle(val project: Project) : CoroutineContext.Element {

    override val key: CoroutineContext.Key<*> = Key

    private val interceptor = Interceptor()
    private val context = this + interceptor

    private var isSpinning = false
    private val queue = ArrayDeque<Continuation<Unit>>()

    fun launch(action: suspend () -> Unit) {
        val coroutine = action.createCoroutine(
            Continuation(context, resumeWith = { result -> result.getOrThrow() })
        )

        queue.add(coroutine)
        if (!isSpinning) {
            spin()
        }
    }

    private fun spin() {
        isSpinning = true
        try {
            while (queue.isNotEmpty()) {
                queue.removeFirst().resume(Unit)
            }
        } finally {
            isSpinning = false
        }
    }

    companion object Key : CoroutineContext.Key<Lifecycle>

    private inner class Interceptor : ContinuationInterceptor {
        override val key: CoroutineContext.Key<*> = ContinuationInterceptor.Key

        override fun <T> interceptContinuation(continuation: Continuation<T>): Continuation<T> {
            val continuationTrace = Throwable("'continuation' trace")

            return Continuation(continuation.context) { result ->
                val resumeWithTrace = Throwable("'resumeWith' trace", continuationTrace)
                queue.add(Continuation(continuation.context) {
                    try {
                        continuation.resumeWith(result)
                    } catch (t: Throwable) {
                        t.addSuppressed(resumeWithTrace)
                        throw t
                    }
                })

                if (!isSpinning) {
                    spin()
                }
            }
        }
    }
}
