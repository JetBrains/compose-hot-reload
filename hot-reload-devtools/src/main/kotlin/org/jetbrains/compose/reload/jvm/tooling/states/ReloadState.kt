package org.jetbrains.compose.reload.jvm.tooling.states

import io.sellmair.evas.State
import io.sellmair.evas.launchState
import io.sellmair.evas.update
import kotlinx.coroutines.CoroutineScope
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.jetbrains.compose.reload.jvm.tooling.orchestration
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import org.jetbrains.compose.reload.orchestration.asFlow

sealed class ReloadState : State {

    abstract val time: Instant

    data class Ok(
        override val time: Instant = Clock.System.now(),
    ) : ReloadState()

    data class Reloading(
        override val time: Instant = Clock.System.now(),
    ) : ReloadState()

    data class Failed(
        val reason: String,
        override val time: Instant = Clock.System.now(),
    ) : ReloadState()

    companion object Key : State.Key<ReloadState> {
        override val default: ReloadState = Ok(time = Clock.System.now())
    }
}


fun CoroutineScope.launchReloadState() = launchState(ReloadState) {
    var currentReloadRequest: OrchestrationMessage.ReloadClassesRequest? = null

    orchestration.asFlow().collect { message ->
        if (message is OrchestrationMessage.RecompileRequest) {
            ReloadState.Reloading().emit()
        }

        if (message is OrchestrationMessage.LogMessage && message.tag == OrchestrationMessage.LogMessage.TAG_COMPILER) {
            if (message.message.contains("executing build...")) {
                currentReloadRequest = null
                ReloadState.Reloading().emit()
            }

            if (message.message.contains("BUILD FAILED")) {
                ReloadState.Failed("Compilation Failed").emit()
            }

            /*
            The build was successful, but this doesn't necessarily mean that a reload request is fired.
            Example: Lets say some code was added, which causes a 'BUILD FAILED'.
            Afterward, the failing code was removed again. A 'BUILD SUCCESSFUL' will be observed w/o firing
            a reload request, because the runtime classpath is not changed to the last working state.

            Because of this, we will set the state to 'OK' if the build was successful, but no
            reload request was observed.
             */
            if (message.message.contains("BUILD SUCCESSFUL")) {
                if (currentReloadRequest == null) {
                    ReloadState.update { state -> state as? ReloadState.Ok ?: ReloadState.Ok() }
                }
            }
        }

        if (message is OrchestrationMessage.ReloadClassesRequest) {
            currentReloadRequest = message
            ReloadState.update { state ->
                state as? ReloadState.Reloading ?: ReloadState.Reloading(time = Clock.System.now())
            }
        }

        if (message is OrchestrationMessage.ReloadClassesResult) {
            if (message.isSuccess) ReloadState.Ok(time = Clock.System.now()).emit()
            else ReloadState.Failed("Failed reloading classes (${message.errorMessage})").emit()
        }
    }
}
