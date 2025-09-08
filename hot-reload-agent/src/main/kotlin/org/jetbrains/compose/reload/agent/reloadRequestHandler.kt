/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.agent


import org.jetbrains.compose.reload.core.Context
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.debug
import org.jetbrains.compose.reload.core.error
import org.jetbrains.compose.reload.core.exception
import org.jetbrains.compose.reload.core.isFailure
import org.jetbrains.compose.reload.core.isSuccess
import org.jetbrains.compose.reload.core.launchTask
import org.jetbrains.compose.reload.core.with
import org.jetbrains.compose.reload.core.withLinearClosure
import org.jetbrains.compose.reload.core.withType
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ReloadClassesRequest
import java.io.File
import java.lang.instrument.Instrumentation

private val logger = createLogger()

internal fun launchReloadRequestHandler(instrumentation: Instrumentation) = launchTask {
    var pendingChanges = mapOf<File, ReloadClassesRequest.ChangeType>()

    orchestration.messages.withType<ReloadClassesRequest>().collect { request ->
        /*
        Important: We want to use the ui thread for the reloading to ensure the UI thread not trying
        render, or work inside Compose in some inconsistent state!

        We block the 'reloadMain' thread to also ensure that no other work can be done
        while we're reloading.
         */
        runOnUiThreadBlocking {
            pendingChanges = pendingChanges + request.changedClassFiles

            val context = Context(ReloadClassesRequest with request)

            executeBeforeHotReloadListeners(request.messageId)
            val result = context.reload(instrumentation, request.messageId, pendingChanges)

            /*
            Yuhuu! We reloaded the classes; We can reset the 'pending changes'; No re-try necessary
             */
            if (result.isSuccess()) {
                logger.debug("Reloaded classes: ${request.messageId}")
                pendingChanges = emptyMap()
                OrchestrationMessage.ReloadClassesResult(request.messageId, true).sendAsync()
            }

            if (result.isFailure()) {
                logger.error("Reload failed", result.exception)
                OrchestrationMessage.ReloadClassesResult(
                    request.messageId, false, result.exception.message,
                    result.exception.withLinearClosure { throwable -> throwable.cause }
                        .flatMap { throwable -> throwable.stackTrace.toList() }
                ).sendAsync()
            }

            executeAfterHotReloadListeners(request.messageId, result)
        }
    }
}
