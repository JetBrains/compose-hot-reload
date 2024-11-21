package org.jetbrains.compose.reload.agent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import org.jetbrains.compose.reload.orchestration.invokeWhenReceived
import java.io.File
import java.lang.instrument.Instrumentation
import kotlin.concurrent.withLock
import kotlin.system.exitProcess

private val logger = createLogger()

internal fun launchReloadClassesRequestHandler(instrumentation: Instrumentation) {
    var pendingChanges = mapOf<File, OrchestrationMessage.ReloadClassesRequest.ChangeType>()

    ComposeHotReloadAgent.orchestration.invokeWhenReceived<OrchestrationMessage.ReloadClassesRequest> { request ->
        runBlocking(Dispatchers.Main) {

            pendingChanges = pendingChanges + request.changedClassFiles

            ComposeHotReloadAgent.executeBeforeReloadListeners(request.messageId)
            val result = runCatching { reload(instrumentation, pendingChanges) }

            /*
            Yuhuu! We reloaded the classes; We can reset the 'pending changes'; No re-try necessary
             */
            if (result.isSuccess) {
                logger.orchestration("Reloaded classes: ${request.messageId}")
                OrchestrationMessage.LogMessage(OrchestrationMessage.LogMessage.TAG_AGENT)
                pendingChanges = emptyMap()
                OrchestrationMessage.ReloadClassesResult(request.messageId, true).send()
            }

            if (result.isFailure) {
                logger.orchestration("Failed to reload classes", result.exceptionOrNull())
                OrchestrationMessage.ReloadClassesResult(
                    request.messageId, false, result.exceptionOrNull()?.message
                ).send()
            }

            ComposeHotReloadAgent.executeAfterReloadListeners(request.messageId, result.exceptionOrNull())
        }
    }

    ComposeHotReloadAgent.orchestration.invokeWhenReceived<OrchestrationMessage.ShutdownRequest> {
        logger.info("Received shutdown request")
        exitProcess(0)
    }

    ComposeHotReloadAgent.orchestration.invokeWhenClosed {
        logger.info("Application Orchestration closed")
        exitProcess(0)
    }
}