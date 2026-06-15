/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.devtools.states

import io.sellmair.evas.State
import io.sellmair.evas.launchState
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.compose.devtools.api.UIErrorState
import org.jetbrains.compose.devtools.asFlow
import org.jetbrains.compose.reload.core.WindowId

/**
 * Devtools-side mirror of the shared [UIErrorState] orchestration state.
 */
data class ErrorUIState(val errors: Map<WindowId, UIErrorDescription>) : State {
    companion object Key : State.Key<ErrorUIState> {
        override val default: ErrorUIState = ErrorUIState(emptyMap())
    }
}

data class UIErrorDescription(
    val title: String,
    val message: String?,
    val stacktrace: List<String>,
)

fun CoroutineScope.launchErrorUIState() = launchState(ErrorUIState) {
    UIErrorState.asFlow().collect { state ->
        ErrorUIState(errors = state.errors.mapValues { (_, error) -> error.toDescription() }).emit()
    }
}

private fun UIErrorState.UIError.toDescription(): UIErrorDescription {
    return UIErrorDescription(
        title = "UI Exception",
        message = message,
        stacktrace = stacktrace,
    )
}
