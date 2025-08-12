/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.orchestration

import org.jetbrains.compose.reload.core.Try

public interface OrchestrationState

public data class OrchestrationStateId<T : OrchestrationState?>(
    val type: Type<T>, private val name: String? = null
) {
    override fun toString(): String {
        return buildString {
            append(type.signature)
            if (name != null) {
                append(" ($name)")
            }
        }
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
}

internal data class OrchestrationStateUpdate(
    val id: OrchestrationStateId<*>,
    val expectedValue: ByteArray?,
    val newValue: ByteArray
) : OrchestrationPackage() {

    data class Response(
        val accepted: Boolean
    ) : OrchestrationPackage()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is OrchestrationStateUpdate) return false
        if (other.id != id) return false
        if (!other.expectedValue.contentEquals(expectedValue)) return false
        if (!other.newValue.contentEquals(newValue)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + expectedValue.contentHashCode()
        result = 31 * result + newValue.contentHashCode()
        return result
    }
}

public data class OrchestrationStateValue(
    val id: OrchestrationStateId<*>,
    val value: ByteArray
) : OrchestrationPackage() {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is OrchestrationStateValue) return false
        if (other.id != id) return false
        if (!other.value.contentEquals(value)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + value.contentHashCode()
        return result
    }
}

public data class OrchestrationStateRequest(
    public val id: OrchestrationStateId<*>
) : OrchestrationPackage()

public interface OrchestrationStateEncoder<T> {
    public val type: Type<T>
    public fun encode(state: T): ByteArray
    public fun decode(data: ByteArray): Try<T>
}
