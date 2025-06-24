/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.devtools

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.filterIsInstance
import org.jetbrains.compose.devtools.api.RecompilerExtension
import org.jetbrains.compose.reload.core.Future
import org.jetbrains.compose.reload.core.Try
import org.jetbrains.compose.reload.core.WorkerThread
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.debug
import org.jetbrains.compose.reload.core.dispatcher
import org.jetbrains.compose.reload.core.error
import org.jetbrains.compose.reload.core.exception
import org.jetbrains.compose.reload.core.info
import org.jetbrains.compose.reload.core.invokeOnError
import org.jetbrains.compose.reload.core.invokeOnFinish
import org.jetbrains.compose.reload.core.isFailure
import org.jetbrains.compose.reload.core.isSuccess
import org.jetbrains.compose.reload.core.launchTask
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.RecompileRequest
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.RecompileResult
import org.jetbrains.compose.reload.orchestration.asChannel
import java.util.ServiceLoader

private val logger = createLogger()

private val recompilerThread = WorkerThread("Recompiler")

@OptIn(ExperimentalCoroutinesApi::class)
internal fun launchRecompiler(): Future<Unit> = launchTask("Recompiler", recompilerThread.dispatcher) task@{
    val messages = orchestration.asChannel()

    invokeOnError { error ->
        logger.error("Recompiler Error: ${error.message}", error)
    }

    invokeOnFinish {
        messages.cancel()
    }

    val recompiler = ServiceLoader.load(RecompilerExtension::class.java, ClassLoader.getSystemClassLoader())
        .firstNotNullOfOrNull { extension -> extension.createRecompiler() }

    if (recompiler == null) {
        logger.error("No suitable '${RecompilerExtension::class.simpleName}' found")
        return@task
    }


    logger.info("Recompiler: '${recompiler.name}'")
    messages.consumeAsFlow().filterIsInstance<RecompileRequest>().conflateAsList()
        .collect recompile@{ pendingRequests ->
            logger.info("Running Recompiler on '${pendingRequests.map { it.messageId }}'")

            /*
            Collect all accumulated pending requests
             */
            val context = RecompilerContextImpl(
                logger = logger, requests = pendingRequests, orchestration = orchestration
            )

            val exitCode = Try {
                withDisposableShutdownHook(context) {
                    useDisposableStoppable(context) {
                        recompiler.buildAndReload(context)
                    }
                }
            }

            if (exitCode.isFailure()) {
                logger.error("BuildSystem: '${recompiler.name}' failed", exitCode.exception)
                pendingRequests.forEach { request ->
                    RecompileResult(request.messageId, null).send()
                }
            }

            if (exitCode.isSuccess()) {
                logger.debug("BuildSystem: '${recompiler.name}' finished w/ exitCode=${exitCode}")
                pendingRequests.forEach { request ->
                    RecompileResult(request.messageId, exitCode.value?.code).send()
                }
            }
        }
}
