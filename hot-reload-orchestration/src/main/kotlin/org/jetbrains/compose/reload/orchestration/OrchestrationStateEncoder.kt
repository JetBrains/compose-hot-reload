/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */


package org.jetbrains.compose.reload.orchestration

import org.jetbrains.compose.reload.InternalHotReloadApi
import org.jetbrains.compose.reload.core.Try
import org.jetbrains.compose.reload.core.Type
import org.jetbrains.compose.reload.core.update
import java.util.ServiceLoader
import java.util.concurrent.atomic.AtomicReference

public interface OrchestrationStateEncoder<T> {
    public val type: Type<T>
    public fun encode(state: T): ByteArray
    public fun decode(data: ByteArray): Try<T>
}

private val encoders = AtomicReference<Map<OrchestrationStateKey<*>, OrchestrationStateEncoder<*>?>>(emptyMap())

@InternalHotReloadApi
@Suppress("UNCHECKED_CAST")
public fun <T : OrchestrationState?> encoderOfOrThrow(key: OrchestrationStateKey<T>): OrchestrationStateEncoder<T> {
    return encoderOf(key) ?: error("Missing encoder for '$key'")
}

@InternalHotReloadApi
@Suppress("UNCHECKED_CAST")
public fun <T : OrchestrationState?> encoderOf(key: OrchestrationStateKey<T>): OrchestrationStateEncoder<T>? {
    val currentEncoders = encoders.get()

    /* Fast path: encoder is already known */
    if (key in currentEncoders) {
        return currentEncoders[key] as OrchestrationStateEncoder<T>?
    }

    /* Slow path, find encoder and update it  */
    val encoder = ServiceLoader.load(OrchestrationStateEncoder::class.java, key::class.java.classLoader)
        .find { it.type == key.id.type }

    val entry = key to encoder
    encoders.update { current -> current + entry }
    return encoder as OrchestrationStateEncoder<T>?
}
