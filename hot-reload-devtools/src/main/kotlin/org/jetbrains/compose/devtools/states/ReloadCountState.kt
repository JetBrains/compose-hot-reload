/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.compose.devtools.states

import io.sellmair.evas.State
import io.sellmair.evas.flow
import io.sellmair.evas.launchState
import io.sellmair.evas.update
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.jetbrains.compose.devtools.orchestration
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ReloadClassesRequest
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ReloadClassesResult
import org.jetbrains.compose.reload.orchestration.asChannel
import org.jetbrains.compose.reload.orchestration.asFlow

data class ReloadCountState(
    val successfulReloads: Int = 0,
    val failedReloads: Int = 0
) : State {
    companion object Key : State.Key<ReloadCountState> {
        override val default: ReloadCountState = ReloadCountState()
    }
}


internal fun CoroutineScope.launchReloadCountState() = launchState(ReloadCountState.Key) {
    ReloadUIState.flow().buffer().distinctUntilChanged().onEach { reloadState ->
        when (reloadState) {
            is ReloadUIState.Failed -> ReloadCountState.update { count ->
                count.copy(failedReloads = count.failedReloads + 1)
            }
            else -> Unit
        }
    }.launchIn(this)

    orchestration.asFlow().filterIsInstance<ReloadClassesRequest>().collectLatest { request ->
        val result = orchestration.asChannel().consumeAsFlow().filterIsInstance<ReloadClassesResult>().first { result ->
            result.reloadRequestId == request.messageId
        }

        if (result.isSuccess && request.changedClassFiles.isNotEmpty()) ReloadCountState.update { count ->
            count.copy(successfulReloads = count.successfulReloads + 1)
        }
    }
}
