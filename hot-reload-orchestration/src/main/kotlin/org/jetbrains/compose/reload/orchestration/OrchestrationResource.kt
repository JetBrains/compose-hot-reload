/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

@file:OptIn(ExperimentalAtomicApi::class)

package org.jetbrains.compose.reload.orchestration

import org.jetbrains.compose.reload.core.Future
import org.jetbrains.compose.reload.core.WorkerThread
import org.jetbrains.compose.reload.core.await
import org.jetbrains.compose.reload.core.update
import org.jetbrains.compose.reload.core.withThread
import java.io.IOException
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi


internal class OrchestrationIO(
    val reader: OrchestrationReader,
    val writer: OrchestrationWriter,
)

internal class OrchestrationResource<T>(
    name: String,
    private val getResource: () -> T,
    private val closeResource: suspend (T) -> Unit
) {
    private val worker: WorkerThread = WorkerThread(name)
    private val onClose = Future<Unit>()
    private val resource by lazy { getResource() }
    private val state = AtomicReference<OrchestrationIOState>(OrchestrationIOState.Active)

    suspend fun <R> enqueue(action: T.() -> R): R = withThread(worker) {
        if (state.load() == OrchestrationIOState.Closed) throw IOException("Stream is closed")
        return@withThread try {
            resource.action()
        } catch (t: Throwable) {
            closeInternal(t)
            throw OrchestrationIOException(t.message, t)
        }
    }

    suspend fun awaitClose(): Result<Unit> {
        return runCatching { onClose.await() }
    }

    suspend fun close() {
        closeInternal(null)
    }

    private suspend fun closeInternal(error: Throwable? = null) {
        state.update { currentState ->
            when (currentState) {
                is OrchestrationIOState.Active -> OrchestrationIOState.Closing(onClose)
                is OrchestrationIOState.Closing -> return currentState.future.await()
                is OrchestrationIOState.Closed -> return
            }
        }

        worker.shutdown().await()
        val closeResourceResult = runCatching { closeResource(resource) }

        state.update { currentState ->
            when (currentState) {
                is OrchestrationIOState.Active -> error("Unexpected '$currentState'")
                is OrchestrationIOState.Closed -> error("Unexpected '$currentState'")
                is OrchestrationIOState.Closing -> OrchestrationIOState.Closed
            }
        }

        val effectiveError = error ?: closeResourceResult.exceptionOrNull()
        onClose.completeWith(if (effectiveError != null) Result.failure(effectiveError) else Result.success(Unit))
    }
}

internal sealed class OrchestrationIOState {
    data object Active : OrchestrationIOState()
    data class Closing(val future: Future<Unit>) : OrchestrationIOState()
    object Closed : OrchestrationIOState()
}
