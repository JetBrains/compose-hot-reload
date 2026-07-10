/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.jvm

import kotlinx.coroutines.flow.update
import org.jetbrains.compose.reload.agent.reloadCompositionState
import org.jetbrains.compose.reload.core.HotReloadEnvironment

/**
 * Resets the current composition in response to an explicit request (e.g. a 'CleanCompositionRequest'
 * or 'RetryFailedCompositionRequest').
 *
 * The strategy is selected by [HotReloadEnvironment.compositionHardResetEnabled]:
 *  - hard reset: a full teardown+rebuild via Compose's hot-reload state
 *    save/load ('reloadCompositionState'), which resets error states and
 *    recreates the scene/window. Required on Compose runtimes that cannot recover from
 *    a subcomposition error (See CMP-10459, CMP-10471).
 *  - light reset: bump 'hotReloadState.key', which only disposes and recomposes the entry point's
 *    subtree — no window recreation. Sufficient when Compose runtime can recover
 *    from subcomposition errors.
 */
internal fun resetComposition() {
    if (HotReloadEnvironment.compositionHardResetEnabled) {
        reloadCompositionState()
    } else {
        hotReloadState.update { state -> state.copy(key = state.key + 1) }
    }
}
