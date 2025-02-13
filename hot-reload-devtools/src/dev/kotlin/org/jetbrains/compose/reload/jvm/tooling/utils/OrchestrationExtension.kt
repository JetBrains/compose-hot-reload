/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.compose.reload.jvm.tooling.utils

import org.jetbrains.compose.reload.jvm.tooling.OrchestrationExtension
import org.jetbrains.compose.reload.orchestration.OrchestrationHandle

internal class AgentOrchestrationExtension: OrchestrationExtension {
    override fun getOrchestration(): OrchestrationHandle {
        return org.jetbrains.compose.reload.agent.orchestration
    }
}
