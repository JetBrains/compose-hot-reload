/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.orchestration

import org.jetbrains.compose.reload.core.MutableState
import org.jetbrains.compose.reload.core.State
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.error
import org.jetbrains.compose.reload.core.exception
import org.jetbrains.compose.reload.core.leftOr
import org.jetbrains.compose.reload.core.map
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private typealias Id = OrchestrationStateId<*>
private typealias BinaryState = MutableState<Binary?>
private typealias DecodedState = MutableState<Any?>

private val none = Any()
private val logger = createLogger()

/**
 * Mutable container for storing encoded/decoded states.
 * Note: This container is synchronized; The lock can be taken externally, using the [withLock] function
 */
internal class OrchestrationStateStorage {
    private val raw = hashMapOf<Id, BinaryState>()
    private val decoded = hashMapOf<OrchestrationStateKey<*>, DecodedState>()
    private val keys = hashMapOf<Id, MutableSet<OrchestrationStateKey<*>>>()
    private val lock = ReentrantLock()

    inline fun <T> withLock(action: () -> T): T {
        return lock.withLock(action)
    }

    fun getEncodedState(id: OrchestrationStateId<*>): State<Binary?> = lock.withLock {
        return raw.getOrPut(id) { MutableState(null) }
    }

    fun <T : OrchestrationState?> getDecodedState(key: OrchestrationStateKey<T>): State<T> = lock.withLock {
        setupKey(key)

        @Suppress("UNCHECKED_CAST")
        return decoded.getValue(key).map { value ->
            if (value == none) key.default
            else value as T
        }
    }

    fun update(id: OrchestrationStateId<*>, encoded: Binary?) = lock.withLock {
        val rawState = raw.getOrPut(id) { MutableState(null) }
        rawState.update { encoded }

        keys[id].orEmpty().forEach { key ->
            val encoder = encoderOfOrThrow(key)
            val decodedState = decoded.getValue(key)
            decodedState.update { encoder.decodeOrNone(encoded ?: return@update none) }
        }
    }

    fun update(
        id: OrchestrationStateId<*>, expectedValue: Binary?, newValue: Binary
    ): Boolean = lock.withLock {
        val rawState = raw.getOrPut(id) { MutableState(null) }
        if (rawState.value != expectedValue) return false
        update(id, newValue)
        return true
    }

    fun <T : OrchestrationState?> update(key: OrchestrationStateKey<T>, value: T) = withLock {
        setupKey(key)
        val encoder = encoderOfOrThrow(key)
        val encoded = encoder.encode(value)
        raw.getValue(key.id).update { Binary(encoded) }
        decoded.getValue(key).update { value }
    }

    private fun setupKey(key: OrchestrationStateKey<*>) = lock.withLock {
        keys.getOrPut(key.id) { mutableSetOf() }.add(key)

        val rawState = raw.getOrPut(key.id) { MutableState(null) }
        val decodedState = decoded.getOrPut(key) { DecodedState(none) }

        /*
        Try decoding the raw state if not done yet.
         */
        val rawBinary = rawState.value
        if (rawBinary != null && decodedState.value == none) {
            val encoder = encoderOfOrThrow(key)
            decodedState.update { encoder.decodeOrNone(rawBinary) }
        }
    }

    private fun <T : OrchestrationState?> OrchestrationStateEncoder<T>.decodeOrNone(encoded: Binary): Any? {
        return decode(encoded.bytes).leftOr {
            logger.error("Failed decoding state", it.exception)
            none
        }
    }
}
