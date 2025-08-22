/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.compose.devtools.states

import io.sellmair.evas.State
import io.sellmair.evas.launchState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import org.jetbrains.compose.devtools.api.ReloadCountState
import org.jetbrains.compose.devtools.asFlow

data class ReloadCountUIState(
    val successfulReloads: Int = 0,
    val failedReloads: Int = 0
) : State {
    companion object Key : State.Key<ReloadCountUIState> {
        override val default: ReloadCountUIState = ReloadCountUIState()
    }
}

internal fun CoroutineScope.launchReloadCountUIState() = launchState(ReloadCountUIState.Key) {
    ReloadCountState.asFlow().collectLatest { state ->
        ReloadCountUIState(state.successfulReloads, state.failedReloads).emit()
    }
}
