/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.orchestration

import org.jetbrains.compose.reload.core.Try
import org.jetbrains.compose.reload.core.Type
import org.jetbrains.compose.reload.core.decode
import org.jetbrains.compose.reload.core.encodeByteArray
import org.jetbrains.compose.reload.core.type
import java.io.Serializable

public interface OrchestrationState

public inline fun <reified T : OrchestrationState?> stateId(name: String? = null): OrchestrationStateId<T> {
    return OrchestrationStateId(type<T>(), name)
}

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

    internal fun encodeToByteArray(): ByteArray = encodeByteArray {
        val encodedType = type.signature.encodeToByteArray()
        writeInt(encodedType.size)
        write(encodedType)

        val encodedName = name?.encodeToByteArray() ?: byteArrayOf()
        writeInt(encodedName.size)
        write(encodedName)
    }

    internal companion object {
        const val serialVersionUID: Long = 0L
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

public inline fun <reified T : OrchestrationState?> stateKey(default: T): OrchestrationStateKey<T> {
    return object : OrchestrationStateKey<T>() {
        override val id: OrchestrationStateId<T> = stateId()
        override val default: T get() = default
    }
}

public inline fun <reified T : OrchestrationState?> stateKey(name: String, default: T): OrchestrationStateKey<T> {
    return object : OrchestrationStateKey<T>() {
        override val id: OrchestrationStateId<T> = stateId(name)
        override val default: T get() = default
    }
}

public abstract class OrchestrationStateKey<T : OrchestrationState?> {
    public abstract val id: OrchestrationStateId<T>
    public abstract val default: T
}

@Deprecated("Replaced for inlining object declarations")
@PublishedApi
internal data class OrchestrationStateKeyImpl<T : OrchestrationState?>(
    override val id: OrchestrationStateId<T>, override val default: T
) : OrchestrationStateKey<T>()
