/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compsoe.reload.analyzer.app.states

import io.sellmair.evas.State
import io.sellmair.evas.launchState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import org.jetbrains.compose.reload.analysis.ClassInfo
import org.jetbrains.compose.reload.analysis.RuntimeInfo
import org.jetbrains.compose.reload.analysis.TrackingRuntimeInfo
import org.jetbrains.compose.reload.analysis.render
import kotlin.io.path.Path
import kotlin.io.path.extension
import kotlin.io.path.walk
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

sealed class RuntimeInfoState : State {

    data object Loading : RuntimeInfoState()
    data class Error(val reason: Throwable?, val message: String? = reason?.message) : RuntimeInfoState()
    data class Result(val info: RuntimeInfo, val rendered: String) : RuntimeInfoState()

    companion object Key : State.Key<RuntimeInfoState?> {
        override val default: RuntimeInfoState? = null
    }
}

fun CoroutineScope.launchRuntimeInfoState() = launchState(
    coroutineContext = Dispatchers.IO,
    keepActive = 1.minutes
) { key: RuntimeInfoState.Key ->
    RuntimeInfoState.Loading.emit()


    while (true) {
        val tracking = TrackingRuntimeInfo()

        Path(".").walk().forEach { path ->
            if (path.extension != "class") return@forEach
            runCatching {
                val info = ClassInfo(path.toFile().readBytes()) ?: return@forEach
                tracking.add(info)
            }
        }

        RuntimeInfoState.Result(tracking.copy(), tracking.render()).emit()

        delay(5.seconds)
    }
}
