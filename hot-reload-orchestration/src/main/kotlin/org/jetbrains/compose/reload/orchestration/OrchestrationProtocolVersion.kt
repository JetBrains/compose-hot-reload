/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.orchestration

import org.jetbrains.compose.reload.core.Try
import org.jetbrains.compose.reload.core.toLeft
import org.jetbrains.compose.reload.core.toRight

public enum class OrchestrationProtocolVersion(public val intValue: Int) {
    V1(1);

    public companion object {
        internal const val serialVersionUID: Long = 0L

        public val current: OrchestrationProtocolVersion get() = V1

        public fun from(intValue: Int): Try<OrchestrationProtocolVersion> {
            entries.firstOrNull { it.intValue == intValue }?.let { return it.toLeft() }
            return IllegalArgumentException("Unknown protocol version: $intValue").toRight()
        }
    }
}
