/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.orchestration

import org.jetbrains.compose.reload.core.Try
import java.util.ServiceLoader

public fun OrchestrationHandle(): Try<OrchestrationHandle> = Try {
    ServiceLoader.load(OrchestrationService::class.java)
        .single()
        .getOrchestration()
}

public interface OrchestrationService {
    public fun getOrchestration(): OrchestrationHandle
}
