/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

@file:OptIn(ExperimentalTime::class)

package org.jetbrains.compose.devtools.states

import io.sellmair.evas.flow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.launch
import org.jetbrains.compose.devtools.api.ReloadState
import org.jetbrains.compose.reload.core.Environment
import org.jetbrains.compose.reload.core.Logger
import org.jetbrains.compose.reload.core.Update
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.trace
import org.jetbrains.compose.reload.orchestration.OrchestrationHandle
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.BuildStarted
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.BuildTaskResult
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.LogMessage
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.RecompileRequest
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ReloadClassesRequest
import org.jetbrains.compose.reload.orchestration.asFlow
import kotlin.time.ExperimentalTime

private val logger = createLogger()

fun CoroutineScope.launchReloadStateActor(
    orchestration: OrchestrationHandle = org.jetbrains.compose.devtools.orchestration
) = launch {
    var inFailedState = false
    val errorLogs = mutableListOf<String>()

    suspend fun update(update: (current: ReloadState) -> ReloadState): Update<ReloadState> {
        val update = orchestration.update(ReloadState, update)
        inFailedState = update.updated is ReloadState.Failed
        if (!inFailedState) {
            errorLogs.clear()
        }
        return update
    }

    launch {
        ReloadUIState.flow().buffer(Channel.UNLIMITED).collect { state ->
            logger.trace { "${ReloadUIState::class.simpleName}=$state" }
        }
    }

    orchestration.asFlow().collect { message ->
        /*
        Handle messages indicating that a reload is requested or active, e.g.:
            * BuildStarted event
            * RecompileRequest
            * execution of any task
         */
        if (message is RecompileRequest ||
            message is BuildStarted ||
            message is LogMessage && message.message.contains("executing build...")
        ) update { state ->
            state as? ReloadState.Reloading ?: ReloadState.Reloading()
        }

        /*
        Handle the failure of any build task -> Failed to reload!
         */
        if (message is BuildTaskResult && !message.isSuccess) update {
            val failureMessage = message.failures.mapNotNull { it.message }.joinToString(", ")
            ReloadState.Failed(reason = failureMessage, details = errorLogs.toList())
        }

        /*
        Scan error messages in logs to provide more information in Failed state
         */
        if (message is LogMessage) {
            val isFailureMessage = message.environment == Environment.build && message.message.contains("BUILD FAILED")
            val isErrorMessage = (message.environment != Environment.devTools && message.level >= Logger.Level.Error) ||
                (message.environment == Environment.build && message.message.contains("e: "))

            if (isErrorMessage) errorLogs.addAll(message.message.split("\n"))

            if (isFailureMessage || (isErrorMessage && inFailedState)) update { state ->
                if (state is ReloadState.Failed) {
                    val newState = ReloadState.Failed(reason = state.reason, details = errorLogs.toList())
                    if (state != newState) newState else state
                }
                else ReloadState.Failed(reason = "Build failed", details = errorLogs.toList())
            }
        }

        if (message is ReloadClassesRequest) update { state ->
            val reloadingState = state as? ReloadState.Reloading ?: ReloadState.Reloading()
            reloadingState.copy(reloadRequestId = message.messageId)
        }

        if (message is OrchestrationMessage.BuildFinished ||
            (message is LogMessage && message.message.contains("BUILD SUCCESSFUL")) ||
            // In continuous/auto mode a 'RecompileRequest' does not start a build,
            // so the recompiler answers it immediately with a 'RecompileResult'.
            (message is OrchestrationMessage.RecompileResult)
        ) update { state ->
            if (state !is ReloadState.Reloading) return@update state
            if (state.reloadRequestId == null) return@update ReloadState.Ok()
            state
        }

        if (message is OrchestrationMessage.ReloadClassesResult) update { state ->
            if (!message.isSuccess) return@update ReloadState.Failed(
                reason = message.errorMessage ?: "N/A",
                details = errorLogs.toList(),
            )

            state as? ReloadState.Ok ?: ReloadState.Ok()
        }
    }
}
