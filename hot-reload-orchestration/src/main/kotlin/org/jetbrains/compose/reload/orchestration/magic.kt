/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.orchestration

internal const val ORCHESTRATION_PROTOCOL_MAGIC_NUMBER = 24111602

internal fun checkMagicNumberOrThrow(magicNumber: Int) {
    if (magicNumber != ORCHESTRATION_PROTOCOL_MAGIC_NUMBER)
        throw OrchestrationIOException("Invalid magic number: $magicNumber")
}
