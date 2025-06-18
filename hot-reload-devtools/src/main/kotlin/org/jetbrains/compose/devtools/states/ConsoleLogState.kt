/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.devtools.states

import io.sellmair.evas.State
import io.sellmair.evas.launchState
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.compose.devtools.orchestration
import org.jetbrains.compose.reload.core.displayString
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import org.jetbrains.compose.reload.orchestration.asFlow
import kotlin.time.Duration.Companion.milliseconds

data class ConsoleLogState(val logs: List<String>) : State {
    companion object Key : State.Key<ConsoleLogState> {
        override val default: ConsoleLogState = ConsoleLogState(emptyList())
        const val LIMIT = 4096
    }

}

internal fun CoroutineScope.launchConsoleLogState() = launchState(ConsoleLogState) {
    val logDeque = ArrayDeque<String>()

    orchestration.asFlow().collect { event ->
        if (event is OrchestrationMessage.LogMessage) {
            logDeque.add(
                event.displayString(
                    useEffects = false,
                    includeThreadName = false, includeTimestamp = false, includeEnvironment = false
                ).removePrefix(" | ")
            )
        }

        if (event is OrchestrationMessage.BuildTaskResult) {
            logDeque.add(event.toLog())
        }

        if (logDeque.size > ConsoleLogState.LIMIT) logDeque.removeFirst()
        ConsoleLogState(logs = logDeque.toList()).emit()
    }
}

private fun OrchestrationMessage.BuildTaskResult.toLog(): String {
    return buildString {
        if (isSuccess) append("☑️ Build | ") else append("❌ Build | ")

        append(taskId)
        val startTime = startTime
        val endTime = endTime
        if (startTime != null && endTime != null) {
            val duration = (endTime - startTime).milliseconds
            append(" (${duration.inWholeMilliseconds} ms)")
        }

        failures.forEach { failure ->
            appendLine()
            if (failure.message != null) append(failure.message) else appendLine("<No message>")
        }
    }
}
