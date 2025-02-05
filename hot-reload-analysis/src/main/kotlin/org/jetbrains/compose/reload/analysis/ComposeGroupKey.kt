/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.analysis

/**
 * The actual key emitted by the Compose Compiler associated with the group.
 * For example, a call like
 * ```kotlin
 * startRestartGroup(1902)
 *```
 *
 * will have the value '1902' recorded as [ComposeGroupKey].
 */
@JvmInline
value class ComposeGroupKey(val key: Int)

fun ComposeGroupKey(key: Int?): ComposeGroupKey? = key?.let(::ComposeGroupKey)

internal object SpecialComposeGroupKeys {
    /**
     * W/o 'OptimizeNonSkippingGroup' each call to remember is wrapped in its own group with this key.
     */
    val remember = ComposeGroupKey(1849434622)
}
