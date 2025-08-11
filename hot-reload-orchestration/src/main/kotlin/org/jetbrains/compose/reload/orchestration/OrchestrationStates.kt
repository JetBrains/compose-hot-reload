/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.orchestration

import org.jetbrains.compose.reload.core.Broadcast
import org.jetbrains.compose.reload.core.Bus
import org.jetbrains.compose.reload.core.State
import org.jetbrains.compose.reload.core.Update
import org.jetbrains.compose.reload.core.withReloadMainThread

public class OrchestrationStates(
    private val broadcast: Bus<OrchestrationMessage>
) {

    private val states = hashMapOf<OrchestrationStateId<*>, State<OrchestrationState>>()
    private val encoders = hashMapOf<Type<*>, OrchestrationStateEncoder<*>>()

    public fun <T : OrchestrationState?> get(key: OrchestrationStateKey<T>): State<T> {
        val current = states[key.id] ?: key.default
        return
    }

    public suspend fun <T : OrchestrationState?> update(
        key: OrchestrationStateKey<T>, update: suspend (T) -> T
    ): Update<T> = withReloadMainThread {
        val encoder = encoders[key.id.type] ?: error("Missing encoder for type: ${key.id.type}")
        val currentState = states[key.id] ?: key.default

        while (true) {
            val nextState = update(currentState)
            val update = OrchestrationStateUpdate(key.id, nextState)

            /
        }
    }
}
