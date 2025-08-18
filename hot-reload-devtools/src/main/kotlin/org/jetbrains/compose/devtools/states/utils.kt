/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.devtools.states

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.compose.devtools.orchestration
import org.jetbrains.compose.reload.orchestration.OrchestrationState
import org.jetbrains.compose.reload.orchestration.OrchestrationStateKey

@Composable
internal fun <T : OrchestrationState?> OrchestrationStateKey<T>.composeState(): State<T> {
    val state = remember { mutableStateOf<T>(default) }
    LaunchedEffect(id) {
        orchestration.states.get(this@composeState).collect { state.value = it }
    }

    return state
}

@Composable
internal fun <T : OrchestrationState?> OrchestrationStateKey<T>.composeValue(): T {
    return composeState().value
}

@Composable
internal fun <T : OrchestrationState?> OrchestrationStateKey<T>.composeFlow(): Flow<T> {
    val state = MutableStateFlow(default)
    LaunchedEffect(id) {
        orchestration.states.get(this@composeFlow).collect { state.value = it }
    }
    return state
}
