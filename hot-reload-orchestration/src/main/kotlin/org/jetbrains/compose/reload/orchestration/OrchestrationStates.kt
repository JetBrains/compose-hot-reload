/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.orchestration

import org.jetbrains.compose.reload.core.CompletableFuture
import org.jetbrains.compose.reload.core.Future
import org.jetbrains.compose.reload.core.Queue
import org.jetbrains.compose.reload.core.State
import org.jetbrains.compose.reload.core.Update
import org.jetbrains.compose.reload.core.complete
import org.jetbrains.compose.reload.core.launchTask


public interface OrchestrationStates {
    public suspend fun <T : OrchestrationState?> get(key: OrchestrationStateKey<T>): State<T>
}

internal class OrchestrationServerStates() : OrchestrationStates {
    private val storage = OrchestrationStateStorage()

    data class StateUpdate<T : OrchestrationState?>(
        val id: OrchestrationStateId<T>,
        val previousState: T,
        val updatedState: T,
        val previousStateEncoded: Binary?,
        val updatedStateEncoded: Binary,
    )


    inline fun <T> withLock(action: () -> T): T {
        return storage.withLock { action() }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : OrchestrationState?> update(
        key: OrchestrationStateKey<T>, update: (T) -> T
    ): Update<T> = storage.withLock {

        val currentState = storage.getDecodedState(key).value
        val nextState = update(currentState)
        storage.update(key, nextState)
        Update(currentState, nextState)
    }

    fun update(
        id: OrchestrationStateId<*>, expectedValue: Binary?, newValue: Binary
    ): Boolean = storage.update(id, expectedValue, newValue)


    @Suppress("UNCHECKED_CAST")
    override suspend fun <T : OrchestrationState?> get(
        key: OrchestrationStateKey<T>
    ): State<T> = storage.getDecodedState(key)

    fun getEncodedState(id: OrchestrationStateId<*>): State<Binary?> =
        storage.getEncodedState(id)

}

internal class OrchestrationClientStates(
    private val outgoingRequests: Queue<OrchestrationStateRequest>
) : OrchestrationStates {
    private val statesRequested = hashSetOf<OrchestrationStateId<*>>()
    private val statesReceived = mutableMapOf<OrchestrationStateId<*>, CompletableFuture<Unit>>()

    private val storage = OrchestrationStateStorage()

    fun update(update: OrchestrationStateValue) {
        update(update.stateId, update.value)
    }

    fun update(id: OrchestrationStateId<*>, encoded: Binary?): Unit = storage.withLock {
        storage.update(id, encoded)
        statesReceived.getOrPut(id) { Future() }.complete()
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun <T : OrchestrationState?> get(key: OrchestrationStateKey<T>): State<T> {
        ensureStateIsAvailable(key.id)
        return storage.getDecodedState(key)
    }

    private suspend fun ensureStateIsAvailable(id: OrchestrationStateId<*>) {
        val stateReceivedFuture = storage.withLock {
            if (statesRequested.add(id)) {
                launchTask {
                    outgoingRequests.send(OrchestrationStateRequest(id))
                }
            }

            statesReceived.getOrPut(id) { Future() }
        }

        stateReceivedFuture.await()
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun <T : OrchestrationState?> encodeUpdate(
        key: OrchestrationStateKey<T>, update: (T) -> T
    ): EncodedStateUpdate<T> {
        ensureStateIsAvailable(key.id)
        val encoder = encoderOfOrThrow(key)

        return storage.withLock {
            val currentStateEncoded = storage.getEncodedState(key.id).value
            val currentStateDecoded = storage.getDecodedState(key).value
            val updatedState = update(currentStateDecoded)
            val updatedStateEncoded = Binary(encoder.encode(updatedState))
            val encoded = OrchestrationStateUpdate(key.id, currentStateEncoded, updatedStateEncoded)
            EncodedStateUpdate(encoded, currentStateDecoded, updatedState)
        }
    }

    class EncodedStateUpdate<T : OrchestrationState?>(
        val encoded: OrchestrationStateUpdate,
        val previousState: T, val updatedState: T
    )
}
