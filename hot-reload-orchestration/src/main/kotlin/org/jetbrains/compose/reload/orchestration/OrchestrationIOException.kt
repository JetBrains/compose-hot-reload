/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.orchestration

import java.io.IOException

public class OrchestrationIOException(
    override val message: String?, override val cause: Throwable? = null
) : IOException(message, cause) {
    internal companion object {
        @Suppress("unused")
        const val serialVersionUID: Long = 0L
    }
}
