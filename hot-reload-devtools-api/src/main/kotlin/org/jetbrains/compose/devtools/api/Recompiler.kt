/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.devtools.api

import org.jetbrains.compose.reload.core.ExitCode

/**
 * Used to handle and execute [org.jetbrains.compose.reload.orchestration.OrchestrationMessage.RecompileRequest]s
 * A [Recompiler] can be provided using the [RecompilerExtension].
 */
public interface Recompiler {
    public val name: String

    /**
     * Called with a list of [RecompilerContext.requests].
     * Should return [ExitCode.success] if the requests were successfully handled and the code was
     * reloaded (if necessary). Any different [ExitCode] can be used to signal failures.
     *
     * - Use [RecompilerContext.logger] to potentially log the progress of the operation.
     * - Use [RecompilerContext.invokeOnDispose] to ensure that resources are properly cleaned up during shutdown or dispose
     * - Use [RecompilerContext.process] to create a new sub-process with default environment variables for hot reload
     *
     * The requests, which this recompile is supposed to handle can be found in [RecompilerContext.requests]
     * This [Recompiler] can communicate with the build-tool or the rest of the application using the provided
     * [RecompilerContext.orchestration]
     */
    public suspend fun buildAndReload(context: RecompilerContext): ExitCode?
}
