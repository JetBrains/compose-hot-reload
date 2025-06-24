/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.devtools.api

import org.jetbrains.compose.reload.core.Disposable
import org.jetbrains.compose.reload.core.Logger
import org.jetbrains.compose.reload.orchestration.OrchestrationHandle
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage

/**
 * See [Recompiler.buildAndReload] for usage.
 */
public interface RecompilerContext {
    public val logger: Logger
    public val requests: List<OrchestrationMessage.RecompileRequest>
    public val orchestration: OrchestrationHandle
    public fun process(builder: ProcessBuilder.() -> Unit = {}): ProcessBuilder
    public fun invokeOnDispose(action: () -> Unit): Disposable
}
