/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.orchestration

import org.jetbrains.compose.reload.InternalHotReloadApi
import org.jetbrains.compose.reload.core.Try
import org.jetbrains.compose.reload.core.Type
import org.jetbrains.compose.reload.core.type
import java.util.ServiceLoader

public interface OrchestrationStateEncoder<T> {
    public val type: Type<T>
    public fun encode(state: T): ByteArray
    public fun decode(data: ByteArray): Try<T>
}

private val encoders: Map<Type<*>, OrchestrationStateEncoder<*>> by lazy {
    ServiceLoader.load(
        OrchestrationStateEncoder::class.java,
        OrchestrationStateEncoder::class.java.classLoader
    ).associateBy { it.type }
}

@InternalHotReloadApi
@Suppress("UNCHECKED_CAST")
public fun <T : OrchestrationState?> encoderOfOrThrow(type: Type<T>): OrchestrationStateEncoder<T> =
    (encoders[type] ?: error("No encoder for '${type.signature}'")) as OrchestrationStateEncoder<T>

@InternalHotReloadApi
@Suppress("UNCHECKED_CAST")
public fun <T : OrchestrationState?> encoderOf(type: Type<T>): OrchestrationStateEncoder<T>? =
    encoders[type]?.let { it as OrchestrationStateEncoder<T> }

@InternalHotReloadApi
public inline fun <reified T : OrchestrationState?> encoderOf(): OrchestrationStateEncoder<T>? =
    encoderOf(type<T>())

@InternalHotReloadApi
public inline fun <reified T : OrchestrationState?> encoderOfOrThrow(): OrchestrationStateEncoder<T> =
    encoderOfOrThrow(type<T>())
