/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.orchestration

import org.jetbrains.compose.reload.core.Future
import org.jetbrains.compose.reload.core.MutableState
import org.jetbrains.compose.reload.core.State
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.error
import org.jetbrains.compose.reload.core.exception
import org.jetbrains.compose.reload.core.isFailure
import org.jetbrains.compose.reload.core.isSuccess
import org.jetbrains.compose.reload.core.map
import org.jetbrains.compose.reload.core.reloadMainThread
import org.jetbrains.compose.reload.core.withThread
import java.util.ServiceLoader

public interface OrchestrationStates {
    public suspend fun <T : OrchestrationState?> get(key: OrchestrationStateKey<T>): State<T>
}

internal class OrchestrationStatesOwner() : OrchestrationStates {
    private val logger = createLogger()

    private val encoders: Map<Type<*>, OrchestrationStateEncoder<*>> =
        ServiceLoader.load(
            OrchestrationStateEncoder::class.java,
            OrchestrationStateEncoder::class.java.classLoader
        ).associateBy { it.type }

    private val none = Any()

    private val encodedStates = hashMapOf<OrchestrationStateId<*>, MutableState<Binary?>>()

    private val decodedStates = hashMapOf<OrchestrationStateId<*>, MutableState<Any?>>()

    data class StateUpdate<T : OrchestrationState?>(
        val id: OrchestrationStateId<T>,
        val previousState: T,
        val updatedState: T,
        val previousStateEncoded: Binary?,
        val updatedStateEncoded: Binary,
    )

    /* Non suspending to use the 'reloadMainThread' as exclusive thread to manipulate state */
    @Suppress("UNCHECKED_CAST")
    fun <T : OrchestrationState?> update(
        key: OrchestrationStateKey<T>, update: (T) -> T
    ): Future<StateUpdate<T>> = reloadMainThread.invokeImmediate {
        val encoder = encoders[key.id.type] ?: error("No encoder for '${key.id.type}'")
        encoder as OrchestrationStateEncoder<T>

        val encodedState = encodedStates.getOrPut(key.id) { MutableState(null) }
        val decodedState = decodedStates.getOrPut(key.id) { MutableState(none) }

        val currentEncoded = encodedState.value

        val currentDecoded = decodedState.value
        val currentState = if (currentDecoded == none) key.default else currentDecoded as T

        val nextState = update(currentState)
        val nextStateEncoded = Binary(encoder.encode(nextState))

        encodedState.update { nextStateEncoded }
        decodedState.update { nextState }

        StateUpdate(key.id, currentState, nextState, currentEncoded, nextStateEncoded)
    }

    /* Non suspending to use the 'reloadMainThread' as exclusive thread to manipulate state */
    fun update(
        id: OrchestrationStateId<*>, expectedValue: ByteArray?, newValue: ByteArray
    ): Future<Boolean> = reloadMainThread.invokeImmediate {
        val binaryState = encodedStates.getOrPut(id) { MutableState(null) }
        val accepted = binaryState.compareAndSet(Binary(expectedValue), Binary(newValue))
        if (!accepted) return@invokeImmediate false

        /* Update the decoded state if the binary state was updated successfully */
        encoders[id.type]?.let { encoder ->
            val decodeResult = encoder.decode(newValue)
            if (decodeResult.isFailure()) {
                logger.error("Failed to decode state '$id'", decodeResult.exception)
            }

            if (decodeResult.isSuccess()) {
                decodedStates.getOrPut(id) { MutableState(none) }.update { decodeResult.value }
            }
        }

        true
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun <T : OrchestrationState?> get(
        key: OrchestrationStateKey<T>
    ): State<T> = withThread(reloadMainThread, isImmediate = true) {
        val decodedState = decodedStates.getOrPut(key.id) { MutableState(none) }
        decodedState.map { value ->
            if (value == none) key.default
            else value as T
        }
    }
}
