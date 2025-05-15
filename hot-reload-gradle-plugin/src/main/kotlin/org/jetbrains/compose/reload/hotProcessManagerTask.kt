/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.gradle.initialization.BuildCancellationToken
import org.gradle.kotlin.dsl.register
import org.gradle.work.DisableCachingByDefault
import org.jetbrains.compose.reload.core.PidFileInfo
import org.jetbrains.compose.reload.core.leftOr
import org.jetbrains.compose.reload.gradle.Future
import org.jetbrains.compose.reload.gradle.PluginStage
import org.jetbrains.compose.reload.gradle.await
import org.jetbrains.compose.reload.gradle.projectFuture
import org.jetbrains.compose.reload.orchestration.OrchestrationClientRole
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ShutdownRequest
import org.jetbrains.compose.reload.orchestration.connectOrchestrationClient
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.io.path.exists
import kotlin.jvm.optionals.getOrNull

internal val Project.hotProcessManagerTask: Future<TaskProvider<ComposeHotReloadProcessManagerTask>> by projectFuture {
    PluginStage.EagerConfiguration.await()
    project.tasks.register<ComposeHotReloadProcessManagerTask>("composeHotReloadProcessManager")
}

@DisableCachingByDefault(because = "This task should always run")
internal abstract class ComposeHotReloadProcessManagerTask : DefaultTask() {

    @get:Internal
    internal val pidFiles = project.files()

    @Inject
    abstract fun getCancellationToken(): BuildCancellationToken

    @TaskAction
    fun cleanup() {
        pidFiles.files.forEach { pidfile ->
            shutdownApplication(pidfile.toPath(), logger)
            getCancellationToken().addCallback(createShutdownAction(pidfile.toPath(), logger))
        }
    }
}

internal fun createShutdownAction(pidfile: Path, logger: Logger?): Runnable = Runnable {
    if (!pidfile.exists()) return@Runnable
    shutdownApplication(pidfile, logger)
}

private fun shutdownApplication(pidfile: Path, logger: Logger? = null) {
    val info = PidFileInfo(pidfile).leftOr { return }
    val pid = info.pid ?: return
    val port = info.orchestrationPort ?: return
    val process = ProcessHandle.of(pid).getOrNull() ?: return

    logger?.quiet("Sending 'ShutdownRequest' to '$port'")

    connectOrchestrationClient(OrchestrationClientRole.Tooling, port).use { client ->
        client.sendMessage(ShutdownRequest("Gradle Build cancelled")).get(15, TimeUnit.SECONDS)
        logger?.quiet("Waiting for process to exit. PID: '$pid'")
        try {
            process.onExit().get(5, TimeUnit.SECONDS)
        } catch (t: Throwable) {
            logger?.error("Failed to shutdown process: PID '$pid', orchestrationPort: '$port'", t)
            throw t
        }
    }
}
