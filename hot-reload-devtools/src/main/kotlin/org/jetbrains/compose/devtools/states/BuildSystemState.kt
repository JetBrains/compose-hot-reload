/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.devtools.states

import io.sellmair.evas.State
import io.sellmair.evas.launchState
import io.sellmair.evas.update
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterIsInstance
import org.jetbrains.compose.reload.core.BuildSystem
import org.jetbrains.compose.reload.core.Try
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.leftOr
import org.jetbrains.compose.reload.core.warn
import org.jetbrains.compose.reload.orchestration.OrchestrationHandle
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import org.jetbrains.compose.reload.orchestration.asFlow

private val logger = createLogger()

sealed class BuildSystemState : State {

    object Unknown : BuildSystemState()

    data class Initialised(val buildSystem: BuildSystem) : BuildSystemState()

    companion object Key : State.Key<BuildSystemState> {
        override val default: BuildSystemState = Unknown
    }
}


fun CoroutineScope.launchBuildSystemState(
    orchestration: OrchestrationHandle = org.jetbrains.compose.devtools.orchestration
) = launchState(BuildSystemState.Key) {
    orchestration.asFlow().filterIsInstance<OrchestrationMessage.LogMessage>().collectLatest { message ->
        if (message.message.contains("Recompiler created: ")) {
            val recompilerName = message.message.removePrefix("Recompiler created: ").drop(1).dropLast(1)
            BuildSystemState.update {
                Try {
                    val buildSystem = BuildSystem.valueOf(recompilerName)
                    BuildSystemState.Initialised(buildSystem)
                }.leftOr {
                    logger.warn("Unknown build system name: '$recompilerName'")
                    BuildSystemState.Unknown
                }
            }
        }
    }
}
