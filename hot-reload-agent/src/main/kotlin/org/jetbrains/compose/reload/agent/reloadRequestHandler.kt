/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.agent


import org.jetbrains.compose.reload.analysis.RedefinitionVerificationException
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.exception
import org.jetbrains.compose.reload.core.isFailure
import org.jetbrains.compose.reload.core.isSuccess
import org.jetbrains.compose.reload.core.withLinearClosure
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import org.jetbrains.compose.reload.orchestration.invokeWhenReceived
import java.io.File
import java.lang.instrument.Instrumentation

private val logger = createLogger()

internal fun launchReloadRequestHandler(instrumentation: Instrumentation) {
    var pendingChanges = mapOf<File, OrchestrationMessage.ReloadClassesRequest.ChangeType>()

    orchestration.invokeWhenReceived<OrchestrationMessage.ReloadClassesRequest> { request ->
        runOnMainThreadBlocking {
            pendingChanges = pendingChanges + request.changedClassFiles

            executeBeforeHotReloadListeners(request.messageId)
            val result = reload(instrumentation, request.messageId, pendingChanges)

            /*
            Yuhuu! We reloaded the classes; We can reset the 'pending changes'; No re-try necessary
             */
            if (result.isSuccess()) {
                logger.orchestration("Reloaded classes: ${request.messageId}")
                OrchestrationMessage.LogMessage(OrchestrationMessage.LogMessage.TAG_AGENT)
                pendingChanges = emptyMap()
                OrchestrationMessage.ReloadClassesResult(
                    request.messageId,
                    OrchestrationMessage.ReloadClassesResult.ResultType.Success
                ).send()
            }

            if (result.isFailure()) {
                logger.orchestration("Failed to reload classes", result.exception)
                val type = when (result.exception) {
                    is RedefinitionVerificationException -> OrchestrationMessage.ReloadClassesResult.ResultType.VerificationError
                    else -> OrchestrationMessage.ReloadClassesResult.ResultType.Failure
                }
                OrchestrationMessage.ReloadClassesResult(
                    request.messageId, type, result.exception.message,
                    result.exception.withLinearClosure { throwable -> throwable.cause }
                        .flatMap { throwable -> throwable.stackTrace.toList() }
                ).send()
            }

            executeAfterHotReloadListeners(request.messageId, result)
        }
    }

    logger.debug("ReloadRequestHandler launched")
}
