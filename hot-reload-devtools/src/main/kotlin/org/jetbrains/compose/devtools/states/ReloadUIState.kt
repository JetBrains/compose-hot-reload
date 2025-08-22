/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

@file:OptIn(ExperimentalTime::class)

package org.jetbrains.compose.devtools.states

import io.sellmair.evas.State
import io.sellmair.evas.flow
import io.sellmair.evas.launchState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.jetbrains.compose.devtools.api.ReloadState
import org.jetbrains.compose.reload.core.Environment
import org.jetbrains.compose.reload.core.Logger
import org.jetbrains.compose.reload.orchestration.OrchestrationHandle
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.LogMessage
import org.jetbrains.compose.reload.orchestration.OrchestrationMessageId
import org.jetbrains.compose.reload.orchestration.asFlow
import kotlin.time.ExperimentalTime


sealed class ReloadUIState : State {

    abstract val time: Instant

    data class Ok(
        override val time: Instant = Clock.System.now(),
    ) : ReloadUIState()

    data class Reloading(
        override val time: Instant = Clock.System.now(),
        val reloadRequestId: OrchestrationMessageId? = null
    ) : ReloadUIState()

    data class Failed(
        val reason: String,
        override val time: Instant = Clock.System.now(),
        val logs: List<LogMessage> = emptyList(),
    ) : ReloadUIState()

    companion object Key : State.Key<ReloadUIState> {
        override val default: ReloadUIState = Ok(time = Clock.System.now())
    }
}

fun CoroutineScope.launchReloadUIState(
    orchestration: OrchestrationHandle = org.jetbrains.compose.devtools.orchestration
) = launchState(ReloadUIState) {

    val errorLogs = mutableListOf<LogMessage>()


    launch {
        orchestration.states.get(ReloadState).collect { state ->
            val time = Instant.fromEpochMilliseconds(state.time.toEpochMilliseconds())
            when (state) {
                is ReloadState.Ok -> ReloadUIState.Ok(time)
                is ReloadState.Failed -> ReloadUIState.Failed(reason = state.reason, time = time, logs = errorLogs)
                is ReloadState.Reloading -> ReloadUIState.Reloading(time, state.reloadRequestId)
            }.emit()
        }
    }

    launch {
        ReloadCountUIState.flow().collectLatest { _ ->
            errorLogs.clear()
            orchestration.asFlow().filterIsInstance<LogMessage>().collect { log ->
                if (log.environment != Environment.devTools && log.level >= Logger.Level.Error) {
                    errorLogs += log
                }

                if (log.environment == Environment.build && log.message.contains("e: ")) {
                    errorLogs += log
                }
            }
        }
    }
}
