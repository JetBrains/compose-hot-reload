/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.jvm.tooling.states

import io.sellmair.evas.State
import io.sellmair.evas.launchState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import org.jetbrains.compose.reload.jvm.tooling.orchestration
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.LogMessage
import org.jetbrains.compose.reload.orchestration.asFlow

data class ConsoleLogState(val logs: List<String>) : State {
    data class Key(val tag: String) : State.Key<ConsoleLogState> {
        override val default: ConsoleLogState = ConsoleLogState(emptyList())
    }

    companion object {
        const val limit = 4096
    }
}

internal fun CoroutineScope.launchConsoleLogState() {
    launchConsoleLogState(ConsoleLogState.Key(LogMessage.TAG_AGENT))
    launchConsoleLogState(ConsoleLogState.Key(LogMessage.TAG_RUNTIME))
    launchConsoleLogState(ConsoleLogState.Key(LogMessage.TAG_COMPILER))
}

internal fun CoroutineScope.launchConsoleLogState(key: ConsoleLogState.Key) = launchState(key) {
    val logDeque = ArrayDeque<String>()
    orchestration.asFlow().filterIsInstance<LogMessage>()
        .filter { message -> message.tag == key.tag }
        .collect { event ->
            logDeque += event.message
            if (logDeque.size > ConsoleLogState.limit) logDeque.removeFirst()
            ConsoleLogState(logs = logDeque.toList()).emit()
        }
}
