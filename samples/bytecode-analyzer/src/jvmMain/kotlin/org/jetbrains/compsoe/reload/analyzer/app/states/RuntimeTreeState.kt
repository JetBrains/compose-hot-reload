package org.jetbrains.compsoe.reload.analyzer.app.states

import io.sellmair.evas.State
import io.sellmair.evas.launchState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import org.jetbrains.compose.reload.InternalHotReloadApi
import org.jetbrains.compose.reload.analysis.renderInstructionTree
import java.nio.file.Path
import kotlin.io.path.readBytes
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

sealed class RuntimeTreeState : State {
    data class Key(val path: Path) : State.Key<RuntimeTreeState?> {
        override val default: RuntimeTreeState? = null
    }

    data object Loading : RuntimeTreeState()
    data class Error(val message: String) : RuntimeTreeState()
    data class Result(val rendered: String) : RuntimeTreeState()
}

@OptIn(InternalHotReloadApi::class)
fun CoroutineScope.launchRuntimeTreeState() = launchState(
    coroutineContext = Dispatchers.IO,
    keepActive = 1.minutes
) { key: RuntimeTreeState.Key ->
    RuntimeTreeState.Loading.emit()
    while (true) {
        runCatching {
            RuntimeTreeState.Result(renderInstructionTree(key.path.readBytes())).emit()
        }.onFailure { exception ->
            RuntimeTreeState.Error(exception.message ?: "Failed to parse runtime info").emit()
        }

        delay(5.seconds)
    }
}
