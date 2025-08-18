/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.orchestration

import org.jetbrains.compose.reload.core.CompletableFuture
import org.jetbrains.compose.reload.core.Future
import org.jetbrains.compose.reload.core.MutableState
import org.jetbrains.compose.reload.core.Queue
import org.jetbrains.compose.reload.core.State
import org.jetbrains.compose.reload.core.Update
import org.jetbrains.compose.reload.core.complete
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.error
import org.jetbrains.compose.reload.core.exception
import org.jetbrains.compose.reload.core.isSuccess
import org.jetbrains.compose.reload.core.launchTask
import org.jetbrains.compose.reload.core.map
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock


public interface OrchestrationStates {
    public suspend fun <T : OrchestrationState?> get(key: OrchestrationStateKey<T>): State<T>
}

private val logger = createLogger()

private val none = Any()

internal class OrchestrationServerStates() : OrchestrationStates {
    private val encodedStates = hashMapOf<OrchestrationStateId<*>, MutableState<Binary?>>()
    private val decodedStates = hashMapOf<OrchestrationStateId<*>, MutableState<Any?>>()
    private val lock = ReentrantLock()

    data class StateUpdate<T : OrchestrationState?>(
        val id: OrchestrationStateId<T>,
        val previousState: T,
        val updatedState: T,
        val previousStateEncoded: Binary?,
        val updatedStateEncoded: Binary,
    )

    @Suppress("UNCHECKED_CAST")
    fun <T : OrchestrationState?> update(
        key: OrchestrationStateKey<T>, update: (T) -> T
    ): Update<T> = lock.withLock {
        val encoder = encoderOf(key.id.type) ?: error("No encoder for '${key.id.type}'")

        val encodedState = encodedStates.getOrPut(key.id) { MutableState(null) }
        val decodedState = decodedStates.getOrPut(key.id) { MutableState(none) }

        val currentDecoded = decodedState.value
        val currentState = if (currentDecoded == none) key.default else currentDecoded as T

        val nextState = update(currentState)
        val nextStateEncoded = Binary(encoder.encode(nextState))

        encodedState.update { nextStateEncoded }
        decodedState.update { nextState }

        Update(currentState, nextState)
    }

    fun update(
        id: OrchestrationStateId<*>, expectedValue: Binary?, newValue: Binary
    ): Boolean = lock.withLock {
        val encodedState = encodedStates.getOrPut(id) { MutableState(null) }
        if (encodedState.value != expectedValue) return@withLock false
        encodedState.update { newValue }


        /* Update the decoded state if the binary state was updated successfully */
        encoderOf(id.type)?.let { encoder ->
            val decode = encoder.decode(newValue.bytes)
            if (decode.isSuccess()) decodedStates.getOrPut(id) { MutableState(none) }.update { decode.value }
            else logger.error("Failed to decode state '$id'", decode.exception)
        }

        true
    }

    inline fun <T> withLock(action: () -> T): T {
        return lock.withLock { action() }
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun <T : OrchestrationState?> get(
        key: OrchestrationStateKey<T>
    ): State<T> = lock.withLock {
        val decodedState = decodedStates.getOrPut(key.id) { MutableState(none) }
        decodedState.map { value ->
            if (value == none) key.default
            else value as T
        }
    }

    fun getEncodedState(id: OrchestrationStateId<*>): State<Binary?> = lock.withLock {
        encodedStates.getOrPut(id) { MutableState(null) }
    }
}

internal class OrchestrationClientStates(
    private val outgoingRequests: Queue<OrchestrationStateRequest>
) : OrchestrationStates {

    private val lock = ReentrantLock()

    private val statesRequested = hashSetOf<OrchestrationStateId<*>>()

    private val statesReceived = mutableMapOf<OrchestrationStateId<*>, CompletableFuture<Unit>>()

    private val encodedStates = hashMapOf<OrchestrationStateId<*>, MutableState<Binary?>>()

    private val decodedStates = hashMapOf<OrchestrationStateId<*>, MutableState<Any?>>()

    fun update(update: OrchestrationStateValue): Unit = lock.withLock {
        update(update.stateId, update.value)
    }

    fun update(id: OrchestrationStateId<*>, encoded: Binary?) = lock.withLock {
        val encodedState = encodedStates.getOrPut(id) { MutableState(null) }
        val decodedState = decodedStates.getOrPut(id) { MutableState(none) }

        encodedState.update { encoded }

        /* Try decoding the value */
        val encoder = encoderOf(id.type)
        if (encoder != null && encoded != null) {
            val decode = encoder.decode(encoded.bytes)
            if (decode.isSuccess()) {
                decodedState.update { decode.value }
            } else logger.error("Failed to decode state '${id}'", decode.exception)
        }

        statesReceived.getOrPut(id) { Future() }.complete()
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun <T : OrchestrationState?> get(key: OrchestrationStateKey<T>): State<T> {
        ensureStateIsAvailable(key.id)
        val state = lock.withLock { decodedStates.getOrPut(key.id) { MutableState(none) } }

        return state.map { value ->
            if (value == none) key.default
            else value as T
        }
    }

    private suspend fun ensureStateIsAvailable(id: OrchestrationStateId<*>) {
        val stateReceivedFuture = lock.withLock {
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

        val encoder = encoderOf(key.id.type) ?: error("No encoder for '${key.id.type}'")

        return lock.withLock {
            val currentStateEncoded = encodedStates[key.id]?.value
            val currentStateDecoded = decodedStates[key.id]?.value
            val currentState = if (currentStateDecoded == none) key.default else currentStateDecoded as T
            val updatedState = update(currentState)
            val updatedStateEncoded = Binary(encoder.encode(updatedState))
            val encoded = OrchestrationStateUpdate(key.id, currentStateEncoded, updatedStateEncoded)
            EncodedStateUpdate(encoded, currentState, updatedState)
        }
    }

    class EncodedStateUpdate<T : OrchestrationState?>(
        val encoded: OrchestrationStateUpdate,
        val previousState: T, val updatedState: T
    )
}
