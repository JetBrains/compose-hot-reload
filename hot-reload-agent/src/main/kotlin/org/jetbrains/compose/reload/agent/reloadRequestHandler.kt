/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.agent


import org.jetbrains.compose.reload.core.exception
import org.jetbrains.compose.reload.core.isFailure
import org.jetbrains.compose.reload.core.isSuccess
import org.jetbrains.compose.reload.core.withLinearClosure
import org.jetbrains.compose.reload.orchestration.HotReloadLogger
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import org.jetbrains.compose.reload.orchestration.invokeWhenReceived
import java.io.File
import java.lang.instrument.Instrumentation

private val logger = HotReloadLogger()

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
                logger.info("Reloaded classes: ${request.messageId}")
                pendingChanges = emptyMap()
                OrchestrationMessage.ReloadClassesResult(request.messageId, true).send()
            }

            if (result.isFailure()) {
                logger.error("Failed to reload classes", result.exception)
                OrchestrationMessage.ReloadClassesResult(
                    request.messageId, false, result.exception.message,
                    result.exception.withLinearClosure { throwable -> throwable.cause }
                        .flatMap { throwable -> throwable.stackTrace.toList() }
                ).send()
            }

            executeAfterHotReloadListeners(request.messageId, result)
        }
    }

    logger.debug("ReloadRequestHandler launched")
}
