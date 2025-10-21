/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.orchestration

import java.io.Serializable

/**
 * Used for encoding and decoding [OrchestrationMessage]s.
 *  See: [OrchestrationMessageEncoder]
 *
 * Each [OrchestrationMessageEncoder] can define an [OrchestrationMessageClassifier] alongside its type representation.
 *
 * @param namespace Defines a namespace for message classifiers. The 'Compose Hot Reload Core' uses "root".
 * Each system can define its own namespace for its own messages and define types
 *
 * @param type Defines a 'type' of the message within the given namespace.
 */
public data class OrchestrationMessageClassifier(
    internal val namespace: String, internal val type: String
) : Serializable {
    internal companion object {
        const val serialVersionUID: Long = 0L
    }

    override fun toString(): String {
        return "$namespace/$type"
    }
}
