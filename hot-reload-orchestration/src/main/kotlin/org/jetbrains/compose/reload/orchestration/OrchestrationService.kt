/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.orchestration

import java.util.ServiceLoader

public fun OrchestrationHandle(): OrchestrationHandle {
    return ServiceLoader.load(OrchestrationService::class.java)
        .singleOrNull()?.getOrchestration()
        ?: error("Could not create orchestration handle")
}

public interface OrchestrationService {
    public fun getOrchestration(): OrchestrationHandle
}
