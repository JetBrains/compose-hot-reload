/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

@file:OptIn(ExperimentalTime::class)

package org.jetbrains.compose.devtools

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.filterIsInstance
import org.jetbrains.compose.devtools.api.RecompilerExtension
import org.jetbrains.compose.reload.core.Environment
import org.jetbrains.compose.reload.core.ExitCode
import org.jetbrains.compose.reload.core.Future
import org.jetbrains.compose.reload.core.HotReloadEnvironment
import org.jetbrains.compose.reload.core.Try
import org.jetbrains.compose.reload.core.WorkerThread
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.debug
import org.jetbrains.compose.reload.core.dispatcher
import org.jetbrains.compose.reload.core.error
import org.jetbrains.compose.reload.core.exception
import org.jetbrains.compose.reload.core.getOrNull
import org.jetbrains.compose.reload.core.info
import org.jetbrains.compose.reload.core.invokeOnError
import org.jetbrains.compose.reload.core.invokeOnFinish
import org.jetbrains.compose.reload.core.isFailure
import org.jetbrains.compose.reload.core.launchTask
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.RecompileRequest
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.RecompileResult
import org.jetbrains.compose.reload.orchestration.asChannel
import java.util.ServiceLoader
import kotlin.time.ExperimentalTime

private val logger = createLogger()

private val recompilerThread = WorkerThread("Recompiler", isDaemon = false)

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

    logger.debug("Recompiler created: '${recompiler.name}'")

    suspend fun runBuild(requests: List<RecompileRequest>): Try<ExitCode?> {
        logger.info("Running '${recompiler.name}'...")
        val context = RecompilerContextImpl(
            logger = createLogger(name = recompiler.name, environment = Environment.build),
            requests = requests, orchestration = orchestration
        )

        val exitCode = Try {
            withDisposableShutdownHook(context) {
                useDisposableStoppable(context) {
                    recompiler.buildAndReload(context)
                }
            }
        }

        if (exitCode.isFailure()) logger.error("BuildSystem: '${recompiler.name}' failed", exitCode.exception)
        else logger.debug("BuildSystem: '${recompiler.name}' finished w/ exitCode=${exitCode}")
        return exitCode
    }

    val requests = messages.consumeAsFlow().filterIsInstance<RecompileRequest>()

    if (HotReloadEnvironment.gradleBuildContinuous) {
        // Continuous/auto mode: 'buildAndReload' launches a daemon that watches the build inputs and
        // recompiles/reloads on its own, it never returns until shutdown. So we run it once and respond with
        // an immediate result on any recompile request.
        subtask("${recompiler.name} (continuous)", recompilerThread.dispatcher) { runBuild(emptyList()) }
        requests.collect { request -> RecompileResult(request.messageId, 0).send() }
    } else {
        // Non-continuous mode: each (batch of) request triggers one build that compiles, reloads if
        // needed, and exits. We wait for that build and report its exit code back to every request.
        requests.conflateAsList().collect { pendingRequests ->
            val exitCode = runBuild(pendingRequests)
            pendingRequests.forEach { request ->
                RecompileResult(request.messageId, exitCode.getOrNull()?.code).send()
            }
        }
    }
}
