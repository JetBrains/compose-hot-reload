/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.jvm.tooling.states

import io.sellmair.evas.State
import io.sellmair.evas.flow
import io.sellmair.evas.launchState
import io.sellmair.evas.update
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.jetbrains.compose.reload.jvm.tooling.orchestration
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.LogMessage.Companion.TAG_AGENT
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.LogMessage.Companion.TAG_COMPILER
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.LogMessage.Companion.TAG_RUNTIME
import org.jetbrains.compose.reload.orchestration.asFlow

sealed class ReloadState : State {

    abstract val time: Instant

    data class Ok(
        override val time: Instant = Clock.System.now(),
    ) : ReloadState()

    data class Reloading(
        override val time: Instant = Clock.System.now(),
    ) : ReloadState()

    data class Failed(
        val reason: String,
        override val time: Instant = Clock.System.now(),
        val logs: List<OrchestrationMessage.LogMessage> = emptyList(),
    ) : ReloadState()

    companion object Key : State.Key<ReloadState> {
        override val default: ReloadState = Ok(time = Clock.System.now())
    }
}


fun CoroutineScope.launchReloadState() = launchState(ReloadState) {
    var currentReloadRequest: OrchestrationMessage.ReloadClassesRequest? = null
    val collectedLogs = mutableListOf<OrchestrationMessage.LogMessage>()

    launch {
        ReloadCountState.flow().collectLatest { reloadCountState ->
            collectedLogs.clear()
            orchestration.asFlow().filterIsInstance<OrchestrationMessage.LogMessage>().collect { message ->
                if (message.tag == TAG_RUNTIME || message.tag == TAG_AGENT) {
                    collectedLogs += message
                }

                if (message.tag == TAG_COMPILER && message.message.contains("e: ")) {
                    collectedLogs += message
                }
            }
        }
    }

    orchestration.asFlow().collect { message ->
        if (message is OrchestrationMessage.RecompileRequest) {
            ReloadState.Reloading().emit()
        }

        if (message is OrchestrationMessage.BuildTaskResult && !message.isSuccess) {
            ReloadState.Failed("Compilation Failed", logs = collectedLogs.toList()).emit()
        }

        if (message is OrchestrationMessage.BuildTaskResult && message.isSuccess) {
            if (currentReloadRequest == null) {
                ReloadState.Reloading().emit()
            }
        }

        if (message is OrchestrationMessage.LogMessage && message.tag == TAG_COMPILER) {
            if (message.message.contains("executing build...")) {
                currentReloadRequest = null
                ReloadState.Reloading().emit()
            }

            if (message.message.contains("BUILD FAILED")) {
                ReloadState.Failed("Compilation Failed", logs = collectedLogs.toList()).emit()
            }

            /*
            The build was successful, but this doesn't necessarily mean that a reload request is fired.
            Example: Lets say some code was added, which causes a 'BUILD FAILED'.
            Afterward, the failing code was removed again. A 'BUILD SUCCESSFUL' will be observed w/o firing
            a reload request, because the runtime classpath is not changed to the last working state.

            Because of this, we will set the state to 'OK' if the build was successful, but no
            reload request was observed.
             */
            if (message.message.contains("BUILD SUCCESSFUL")) {
                if (currentReloadRequest == null) {
                    ReloadState.update { state -> state as? ReloadState.Ok ?: ReloadState.Ok() }
                }
            }
        }

        if (message is OrchestrationMessage.ReloadClassesRequest) {
            currentReloadRequest = message
            ReloadState.update { state ->
                state as? ReloadState.Reloading ?: ReloadState.Reloading(time = Clock.System.now())
            }
        }

        if (message is OrchestrationMessage.ReloadClassesResult) {
            if (message.isSuccess) ReloadState.Ok(time = Clock.System.now()).emit()
            else ReloadState.Failed(
                "Failed reloading classes (${message.errorMessage})",
                logs = collectedLogs.toList()
            ).emit()
        }
    }
}
