/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.devtools.states

import io.sellmair.evas.State
import io.sellmair.evas.launchState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import org.jetbrains.compose.devtools.orchestration
import org.jetbrains.compose.devtools.sendAsync
import org.jetbrains.compose.reload.core.WindowId
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import org.jetbrains.compose.reload.orchestration.asFlow

data class ErrorUIState(val errors: Map<WindowId, UIErrorDescription>) : State {
    companion object Key : State.Key<ErrorUIState> {
        override val default: ErrorUIState = ErrorUIState(emptyMap())
    }
}

data class UIErrorDescription(
    val title: String,
    val message: String?,
    val stacktrace: List<StackTraceElement>,
    val recovery: (() -> Unit)? = null
)

fun CoroutineScope.launchErrorUIState() = launchState(ErrorUIState) {
    val errors = mutableMapOf<WindowId, UIErrorDescription>()

    suspend fun update() {
        ErrorUIState(errors = errors.toMap()).emit()
    }

    launch {
        orchestration.asFlow().filterIsInstance<OrchestrationMessage.UIException>().collect { message ->
            val windowId = message.windowId ?: return@collect
            errors[windowId] = message.toDescription()
            update()
        }
    }

    launch {
        orchestration.asFlow().filterIsInstance<OrchestrationMessage.UIRendered>().collect { message ->
            val windowId = message.windowId ?: return@collect
            errors.remove(windowId)
            update()
        }
    }
}

private fun OrchestrationMessage.UIException.toDescription(): UIErrorDescription {
    return UIErrorDescription(
        title = "UI Exception",
        message = message,
        stacktrace = stacktrace,
        recovery = { OrchestrationMessage.RetryFailedCompositionRequest().sendAsync() }
    )
}
