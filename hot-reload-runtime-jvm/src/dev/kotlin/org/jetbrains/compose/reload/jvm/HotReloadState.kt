package org.jetbrains.compose.reload.jvm

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.jetbrains.compose.reload.agent.ComposeHotReloadAgent
import java.util.UUID

internal data class HotReloadState(
    val reloadRequestId: UUID? = null,
    val iteration: Int,
    val error: Throwable? = null,
    val key: Int = 0,
) {
    override fun toString(): String {
        return buildString {
            append("{ ")
            append("iteration=$iteration, ")
            append("key=$key, ")
            if (error != null) append("error=${error.message}, ")
            append(" }")
        }
    }
}

internal val hotReloadStateLocal = staticCompositionLocalOf<HotReloadState?> { null }

internal val hotReloadState: MutableStateFlow<HotReloadState> = MutableStateFlow(HotReloadState(null, 0)).apply {
    ComposeHotReloadAgent.invokeAfterReload { reloadRequestId: UUID, error ->
        update { it.copy(reloadRequestId = reloadRequestId, iteration = it.iteration + 1, error = error) }
    }
}
