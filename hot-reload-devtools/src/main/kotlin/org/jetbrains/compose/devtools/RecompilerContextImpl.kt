/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.devtools

import org.jetbrains.compose.devtools.api.RecompilerContext
import org.jetbrains.compose.reload.core.Disposable
import org.jetbrains.compose.reload.core.HotReloadProperty
import org.jetbrains.compose.reload.core.Logger
import org.jetbrains.compose.reload.core.error
import org.jetbrains.compose.reload.core.update
import org.jetbrains.compose.reload.core.withHotReloadEnvironmentVariables
import org.jetbrains.compose.reload.orchestration.OrchestrationHandle
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import java.util.concurrent.atomic.AtomicReference

internal class RecompilerContextImpl(
    override val logger: Logger,
    override val requests: List<OrchestrationMessage.RecompileRequest>,
    override val orchestration: OrchestrationHandle
) : RecompilerContext, Disposable {
    private sealed class State {
        data class Active(val actions: List<() -> Unit>) : State()
        object Disposed : State()
    }

    private val state = AtomicReference<State>(State.Active(emptyList()))

    override fun invokeOnDispose(action: () -> Unit): Disposable {
        val wrapper = { action() }

        state.update { current ->
            when (current) {
                is State.Disposed -> {
                    action()
                    current
                }
                is State.Active -> State.Active(current.actions + wrapper)
            }
        }

        return Disposable {
            state.update { current ->
                when (current) {
                    is State.Disposed -> current
                    is State.Active -> current.copy(actions = current.actions - wrapper)
                }
            }
        }
    }

    override fun dispose() {
        val previous = state.update { current ->
            when (current) {
                is State.Disposed -> current
                is State.Active -> State.Disposed
            }
        }.previous

        if (previous is State.Active) {
            previous.actions.forEach { action ->
                try {
                    action()
                } catch (e: Throwable) {
                    logger.error("Exception while disposing BuildContext", e)
                }
            }
        }
    }

    override fun process(builder: ProcessBuilder.() -> Unit): ProcessBuilder {
        val processBuilder = ProcessBuilder()
        processBuilder.withHotReloadEnvironmentVariables(HotReloadProperty.Environment.BuildTool)
        builder(processBuilder)
        return processBuilder
    }
}
