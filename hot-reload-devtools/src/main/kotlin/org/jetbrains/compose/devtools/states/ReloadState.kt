/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.devtools.states

import io.sellmair.evas.State
import io.sellmair.evas.flow
import io.sellmair.evas.launchState
import io.sellmair.evas.update
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.jetbrains.compose.reload.core.Environment
import org.jetbrains.compose.reload.core.Logger
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.info
import org.jetbrains.compose.reload.core.trace
import org.jetbrains.compose.reload.orchestration.OrchestrationHandle
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.BuildStarted
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.BuildTaskResult
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.LogMessage
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ReloadClassesRequest
import org.jetbrains.compose.reload.orchestration.asFlow

private val logger = createLogger()

sealed class ReloadState : State {

    abstract val time: Instant

    data class Ok(
        override val time: Instant = Clock.System.now(),
    ) : ReloadState()

    data class Reloading(
        override val time: Instant = Clock.System.now(),
        val request: ReloadClassesRequest? = null
    ) : ReloadState()

    data class Failed(
        val reason: String,
        override val time: Instant = Clock.System.now(),
        val logs: List<LogMessage> = emptyList(),
    ) : ReloadState()

    companion object Key : State.Key<ReloadState> {
        override val default: ReloadState = Ok(time = Clock.System.now())
    }
}

fun CoroutineScope.launchReloadState(
    orchestration: OrchestrationHandle = org.jetbrains.compose.devtools.orchestration
) = launchState(ReloadState) {
    val errorLogs = mutableListOf<LogMessage>()

    launch {
        ReloadCountState.flow().collectLatest { _ ->
            errorLogs.clear()
            orchestration.asFlow().filterIsInstance<LogMessage>().collect { log ->
                if (log.level >= Logger.Level.Error) {
                    errorLogs += log
                }

                if (log.environment == Environment.build && log.message.contains("e: ")) {
                    errorLogs += log
                }
            }
        }
    }

    launch {
        ReloadState.flow().buffer(Channel.UNLIMITED).collect { state ->
            logger.trace { "${ReloadState::class.simpleName}=$state" }
        }
    }

    orchestration.asFlow().collect { message ->
        /*
        Handle messages indicating that a reload is active
        (e.g. a BuildStarted event, or the execution of any task)
         */
        if (message is BuildStarted ||
            message is LogMessage && message.message.contains("executing build...")
        ) ReloadState.update { state ->
            state as? ReloadState.Reloading ?: ReloadState.Reloading()
        }

        /*
        Handle the failure of any build task -> Failed to reload!
         */
        if (message is BuildTaskResult && !message.isSuccess) ReloadState.update {
            val failureMessage = message.failures.mapNotNull { it.message }.joinToString(", ")
            ReloadState.Failed(reason = failureMessage, logs = errorLogs.toList())
        }

        if (
            message is LogMessage && message.message.contains("BUILD FAILED")
        ) ReloadState.update { state ->
            state as? ReloadState.Failed ?: ReloadState.Failed(reason = "Build failed", logs = errorLogs.toList())
        }

        if (message is ReloadClassesRequest) ReloadState.update { state ->
            val reloadingState = state as? ReloadState.Reloading ?: ReloadState.Reloading()
            reloadingState.copy(request = message)
        }

        if (message is OrchestrationMessage.BuildFinished ||
            (message is LogMessage && message.message.contains("BUILD SUCCESSFUL"))
        ) ReloadState.update { state ->
            if (state !is ReloadState.Reloading) return@update state
            if (state.request == null) return@update ReloadState.Ok()
            state
        }

        if (message is OrchestrationMessage.ReloadClassesResult) ReloadState.update { state ->
            if (!message.isSuccess) return@update ReloadState.Failed(
                reason = message.errorMessage ?: "N/A",
                logs = errorLogs.toList()
            )

            state as? ReloadState.Ok ?: ReloadState.Ok()
        }
    }
}
