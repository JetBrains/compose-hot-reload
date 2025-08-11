/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.orchestration

import org.jetbrains.compose.reload.core.Try

public interface OrchestrationState

public data class OrchestrationStateId<T : OrchestrationState?>(
    val type: Type<T>, private val name: String? = null
)

public data class OrchestrationStateKey<T : OrchestrationState?>(
    val id: OrchestrationStateId<T>, val default: T
)

@JvmInline
public value class Type<@Suppress("unused") T> @PublishedApi internal constructor(internal val classId: String) {
    public companion object {
        public inline fun <reified T> create(): Type<T> {
            return Type(T::class.java.simpleName.replace(".", "/"))
        }
    }
}

public class OrchestrationStateUpdate(
    public val id: OrchestrationStateId<*>, public val state: ByteArray
) : OrchestrationMessage() {
    internal companion object Companion {
        const val serialVersionUID: Long = 0L
    }
}

public interface OrchestrationStateEncoder<T : OrchestrationState> {
    public val type: Type<T>
    public fun encode(state: T): ByteArray
    public fun decode(data: ByteArray): Try<T>
}
