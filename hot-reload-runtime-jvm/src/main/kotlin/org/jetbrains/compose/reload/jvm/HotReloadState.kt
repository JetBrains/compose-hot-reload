/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.jvm

import androidx.compose.runtime.compositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.jetbrains.compose.reload.agent.invokeAfterHotReload
import org.jetbrains.compose.reload.core.exceptionOrNull
import org.jetbrains.compose.reload.orchestration.OrchestrationMessageId

internal data class HotReloadState(
    val reloadRequestId: OrchestrationMessageId? = null,
    val iteration: Int,
    val reloadError: Throwable? = null,
    val uiError: Throwable? = null,
    val key: Int = 0,
) {
    override fun toString(): String {
        return buildString {
            append("{ ")
            append("iteration=$iteration, ")
            append("key=$key, ")
            if (reloadError != null) append("error=${reloadError.message}, ")
            if (uiError != null) append("uiError=${uiError.message}, ")
            append(" }")
        }
    }
}

internal val hotReloadStateLocal = compositionLocalOf<HotReloadState?> { null }

internal val hotReloadState: MutableStateFlow<HotReloadState> = MutableStateFlow(HotReloadState(null, 0)).apply {
    invokeAfterHotReload { reloadRequestId: OrchestrationMessageId, result ->
        update { state ->
            state.copy(
                reloadRequestId = reloadRequestId,
                iteration = state.iteration + 1,
                reloadError = result.exceptionOrNull(),
                key = state.key + if (state.uiError != null) 1 else 0,
            )
        }
    }
}
