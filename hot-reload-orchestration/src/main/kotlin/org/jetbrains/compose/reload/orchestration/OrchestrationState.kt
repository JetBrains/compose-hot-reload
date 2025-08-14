/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.orchestration

import org.jetbrains.compose.reload.InternalHotReloadApi
import org.jetbrains.compose.reload.core.Try
import org.jetbrains.compose.reload.core.decode
import org.jetbrains.compose.reload.core.encode
import java.io.Serializable
import java.util.ServiceLoader

public interface OrchestrationState

public data class OrchestrationStateId<T : OrchestrationState?>(
    val type: Type<T>, private val name: String? = null
) : Serializable {
    override fun toString(): String {
        return buildString {
            append(type.signature)
            if (name != null) {
                append(" ($name)")
            }
        }
    }

    internal fun encodeToByteArray(): ByteArray = encode {
        val encodedType = type.signature.encodeToByteArray()
        writeInt(encodedType.size)
        write(encodedType)

        val encodedName = name?.encodeToByteArray() ?: byteArrayOf()
        writeInt(encodedName.size)
        write(encodedName)
    }
}

internal fun ByteArray.decodeOrchestrationStateId(): Try<OrchestrationStateId<*>> = Try {
    decode {
        val encodedTypeLength = readInt()
        if (encodedTypeLength !in 0..256) error("Invalid type length: $encodedTypeLength")
        val decodedType = readNBytes(encodedTypeLength).decodeToString()

        val decodedNameLength = readInt()
        if (decodedNameLength !in 0..128) error("Invalid name length: $decodedNameLength")
        val decodedName = if (decodedNameLength == 0) null else readNBytes(decodedNameLength).decodeToString()

        OrchestrationStateId(Type(decodedType), decodedName)
    }
}


public data class OrchestrationStateKey<T : OrchestrationState?>(
    val id: OrchestrationStateId<T>, val default: T
)

public inline fun <reified T> Type(): Type<T> {
    return Type.create()
}

@JvmInline
public value class Type<@Suppress("unused") T> @PublishedApi internal constructor(
    internal val signature: String
) {
    public companion object {
        public inline fun <reified T> create(): Type<T> {
            val isNullable = null is T
            return Type(T::class.java.simpleName.replace(".", "/") + if (isNullable) "?" else "")
        }
    }

    public val isNullable: Boolean get() = signature.endsWith("?")

    override fun toString(): String {
        return signature
    }
}

internal data class OrchestrationStateUpdate(
    val stateId: OrchestrationStateId<*>,
    val expectedValue: Binary?,
    val updatedValue: Binary
) : OrchestrationPackage(), Serializable {
    data class Response(val accepted: Boolean) : OrchestrationPackage()
}

internal data class OrchestrationStateRequest(
    val stateId: OrchestrationStateId<*>
) : OrchestrationPackage(), Serializable

internal data class OrchestrationStateValue(
    val stateId: OrchestrationStateId<*>, val value: Binary?
) : OrchestrationPackage(), Serializable


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
    encoderOf(Type<T>())

@InternalHotReloadApi
public inline fun <reified T : OrchestrationState?> encoderOfOrThrow(): OrchestrationStateEncoder<T> =
    encoderOfOrThrow(Type<T>())
