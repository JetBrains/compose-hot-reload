/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.orchestration

import org.jetbrains.compose.reload.core.Try
import org.jetbrains.compose.reload.core.toLeft
import org.jetbrains.compose.reload.core.toRight

@Deprecated("Use 'OrchestrationVersion' instead.", ReplaceWith("OrchestrationVersion"))
public enum class OrchestrationProtocolVersion(public val intValue: Int) {
    V1(1),
    V1_1(2);

    public companion object {
        internal const val serialVersionUID: Long = 0L

        public val current: OrchestrationProtocolVersion get() = V1_1

        public fun from(intValue: Int): Try<OrchestrationProtocolVersion> {
            entries.firstOrNull { it.intValue == intValue }?.let { return it.toLeft() }
            return IllegalArgumentException("Unknown protocol version: $intValue").toRight()
        }
    }
}

public data class OrchestrationVersion(public val intValue: Int) : Comparable<OrchestrationVersion> {
    public companion object {
        public val v1: OrchestrationVersion = OrchestrationVersion(1)
        public val v1_1: OrchestrationVersion = OrchestrationVersion(2)
        public val current: OrchestrationVersion = v1_1
    }

    override fun compareTo(other: OrchestrationVersion): Int {
        return this.intValue.compareTo(other.intValue)
    }

    override fun toString(): String {
        return when (this) {
            v1 -> "v1($intValue)"
            v1_1 -> "v1.1($intValue)"
            else -> "N/A($intValue)"
        }
    }
}

internal val OrchestrationVersion.supportsStates: Boolean
    get() = this >= OrchestrationVersion.v1_1
