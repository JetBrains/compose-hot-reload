/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.devtools

import org.jetbrains.compose.reload.core.BuildSystem
import org.jetbrains.compose.reload.core.BuildSystem.Amper
import org.jetbrains.compose.reload.core.BuildSystem.Gradle
import org.jetbrains.compose.reload.core.Future
import org.jetbrains.compose.reload.core.HotReloadEnvironment
import org.jetbrains.compose.reload.core.HotReloadProperty
import org.jetbrains.compose.reload.core.HotReloadProperty.Environment.BuildTool
import org.jetbrains.compose.reload.core.LaunchMode
import org.jetbrains.compose.reload.core.Os
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.destroyWithDescendants
import org.jetbrains.compose.reload.core.getOrThrow
import org.jetbrains.compose.reload.core.invokeOnCompletion
import org.jetbrains.compose.reload.core.invokeOnError
import org.jetbrains.compose.reload.core.invokeOnValue
import org.jetbrains.compose.reload.core.launchTask
import org.jetbrains.compose.reload.core.subprocessDefaultArguments
import org.jetbrains.compose.reload.core.withHotReloadEnvironmentVariables
import org.jetbrains.compose.reload.core.withType
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.LogMessage
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.LogMessage.Companion.TAG_COMPILER
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.LogMessage.Companion.TAG_DEVTOOLS
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.RecompileRequest
import java.io.File
import java.nio.file.Path
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.io.path.pathString
import kotlin.streams.asSequence

private val logger = createLogger()

private val buildSystem: BuildSystem? = HotReloadEnvironment.buildSystem

private val gradleBuildRoot: Path? = HotReloadEnvironment.gradleBuildRoot
private val gradleBuildProject: String? = HotReloadEnvironment.gradleBuildProject
private val gradleBuildTask: String? = HotReloadEnvironment.gradleBuildTask
private val isGradleDaemon = run {
    if (HotReloadEnvironment.buildSystem != Gradle) return@run false
    if (!HotReloadEnvironment.gradleBuildContinuous) return@run true
    when (HotReloadEnvironment.launchMode) {
        LaunchMode.Ide, LaunchMode.Detached -> true
        LaunchMode.GradleBlocking -> false
        null -> false
    }
}


private val amperBuildRoot: String? = HotReloadEnvironment.amperBuildRoot
private val amperBuildTask: String? = HotReloadEnvironment.amperBuildTask

private val recompileRequests = LinkedBlockingQueue(
    /*
    Starting with one initial recompile request.

    For continuous builds:
     This request is the start signal, starting a single continuous build for Gradle.

    For non-continuous builds:
      This will warm up the recompiler.
      For Gradle, in particular, the tracking of the classpath
      is incremental; this means that the file collection has to be built initially (with this request)
     */
    listOfNotNull(if (HotReloadEnvironment.gradleBuildContinuous) RecompileRequest() else null)
)

internal fun launchRecompiler(): Future<Unit> = launchTask("Recompiler") task@{
    if (buildSystem == null) return@task
    invokeOnError { error ->
        logger.error("Recompiler Error: ${error.message}", error)
    }

    val port = orchestration.port.await().getOrThrow()
    logger.debug("'Recompiler': Using orchestration at '$port'")

    val processBuilder = when (buildSystem) {
        Amper -> {
            val amperBuildRoot = amperBuildRoot ?: run {
                logger.error("Missing '${HotReloadProperty.AmperBuildRoot.key}' property")
                return@task
            }

            val amperBuildTask = amperBuildTask ?: run {
                logger.error("Missing '${HotReloadProperty.AmperBuildTask.key}' property")
                return@task
            }

            createRecompilerProcessBuilder(
                amperBuildRoot = amperBuildRoot,
                amperBuildTask = amperBuildTask,
                orchestrationPort = port
            )
        }

        Gradle -> {
            val composeBuildRoot = gradleBuildRoot ?: run {
                logger.error("Missing '${HotReloadProperty.GradleBuildRoot.key}' property")
                return@task
            }

            val gradleBuildProject = gradleBuildProject ?: run {
                logger.error("Missing '${HotReloadProperty.GradleBuildProject.key}' property")
                return@task
            }

            val gradleBuildTask = gradleBuildTask ?: run {
                logger.error("Missing '${HotReloadProperty.GradleBuildTask.key}' property")
                return@task
            }

            createRecompilerProcessBuilder(
                gradleBuildRoot = composeBuildRoot,
                gradleBuildProject = gradleBuildProject,
                gradleBuildTask = gradleBuildTask,
                orchestrationPort = port
            )
        }
    }


    val recompilerThread = thread(name = "Recompiler") {
        try {
            while (true) {
                val requests = takeRecompileRequests()
                logger.debug("'Recompiler': Requests: ${requests.map { it.messageId }}")
                val exitCode = processBuilder.startRecompilerProcess()
                logger.debug("'Recompiler': Requests: ${requests.map { it.messageId }}: Exit code: $exitCode")
                requests.forEach { request ->
                    OrchestrationMessage.RecompileResult(
                        recompileRequestId = request.messageId,
                        exitCode = exitCode
                    ).sendAsync()
                }
            }
        } catch (_: InterruptedException) {
            logger.debug("'Recompiler': Interrupted: Shutting down")
        }
    }

    orchestration.messages.withType<RecompileRequest>().invokeOnValue { value ->
        recompileRequests.put(value)
    }

    orchestration.invokeOnCompletion {
        logger.debug("'Recompiler': Sending close signal")
        recompilerThread.interrupt()
        recompilerThread.join()
    }
}

private fun takeRecompileRequests(): List<RecompileRequest> {
    val result = mutableListOf<RecompileRequest>()
    while (recompileRequests.isNotEmpty()) {
        result += recompileRequests.poll()
    }

    if (result.isNotEmpty()) return result.toList()
    return listOf(recompileRequests.take())
}

private fun ProcessBuilder.startRecompilerProcess(): Int? {
    LogMessage(TAG_DEVTOOLS, "Starting recompiler process:\n${this.command().joinToString("        \n")}").sendAsync()

    val process: Process = start()
    logger.debug("'Recompiler': Started (${process.pid()})")

    val shutdownHook = thread(start = false) {
        logger.debug("'Recompiler': Destroying process (Shutdown)")
        process.destroyRecompilerProcess()
    }

    Runtime.getRuntime().addShutdownHook(shutdownHook)

    thread(name = "Recompiler Output", isDaemon = true) {
        process.inputStream.bufferedReader().use { reader ->
            while (true) {
                val nextLine = reader.readLine() ?: break
                LogMessage(TAG_COMPILER, nextLine).sendAsync()
            }
        }
    }

    val exitCode = try {
        process.waitFor()
    } catch (_: InterruptedException) {
        logger.debug("'Recompiler': Destroying process")
        process.destroyRecompilerProcess()
        if (!process.waitFor(15, TimeUnit.SECONDS)) {
            logger.debug("'Recompiler': Force destroying process (Interrupt)")
            process.destroyWithDescendants()
        }
        null
    }


    logger.debug("'Recompiler': finished '$exitCode'")
    runCatching { Runtime.getRuntime().removeShutdownHook(shutdownHook) }
    return exitCode
}

private fun createRecompilerProcessBuilder(
    gradleBuildRoot: Path,
    gradleBuildProject: String,
    gradleBuildTask: String,
    orchestrationPort: Int
): ProcessBuilder {
    return ProcessBuilder().directory(gradleBuildRoot.toFile())
        .command(
            createRecompilerGradleCommandLineArgs(
                gradleBuildProject = gradleBuildProject,
                gradleBuildTask = gradleBuildTask,
                orchestrationPort = orchestrationPort
            )
        )
        .withHotReloadEnvironmentVariables(BuildTool)
        .apply { environment().putIfAbsent("JAVA_HOME", HotReloadEnvironment.gradleJavaHome?.pathString ?: "") }
        .redirectErrorStream(true)
}

private fun createRecompilerProcessBuilder(
    amperBuildRoot: String,
    amperBuildTask: String,
    orchestrationPort: Int,
): ProcessBuilder {
    return ProcessBuilder().directory(File(amperBuildRoot))
        .command(createRecompilerAmperCommandLineArgs(amperBuildTask))
        .withHotReloadEnvironmentVariables(BuildTool)
        .apply { environment().putIfAbsent("COMPOSE_HOT_RELOAD_ORCHESTRATION_PORT", orchestrationPort.toString()) }
        .redirectErrorStream(true)
}

private fun createRecompilerGradleCommandLineArgs(
    gradleBuildProject: String,
    gradleBuildTask: String,
    orchestrationPort: Int
): List<String> {
    if (HotReloadEnvironment.gradleJavaHome == null) {
        logger.warn("Missing '${HotReloadProperty.GradleJavaHome}' property. Using system java")
    }

    val gradleScriptCommand = if (Os.currentOrNull() == Os.Windows) arrayOf("cmd", "/c", "gradlew.bat")
    else arrayOf("./gradlew")


    val gradleTaskPath = if (gradleBuildProject == ":") ":$gradleBuildTask"
    else "$gradleBuildProject:$gradleBuildTask"

    return listOfNotNull(
        *gradleScriptCommand,
        gradleTaskPath,
        "--console=plain",

        "-D${HotReloadProperty.IsHotReloadBuild.key}=true",
        "-P${HotReloadProperty.IsHotReloadBuild.key}=true",
        *subprocessDefaultArguments(BuildTool, orchestrationPort).toTypedArray(),
        "-D${HotReloadProperty.GradleJavaHome.key}=${HotReloadEnvironment.gradleJavaHome?.pathString}"
            .takeIf { HotReloadEnvironment.gradleJavaHome != null },

        /* Continuous mode arguments */
        "-t".takeIf { HotReloadEnvironment.gradleBuildContinuous },
        "--no-daemon".takeIf { !isGradleDaemon },
    )
}

private fun createRecompilerAmperCommandLineArgs(amperBuildTask: String): List<String> {
    val amperScriptCommand = if (Os.currentOrNull() == Os.Windows) arrayOf("cmd", "/c", "amper.bat")
    else arrayOf("./amper")

    return listOfNotNull(
        *amperScriptCommand,
        "task",
        amperBuildTask,
    )
}

private fun Process.destroyRecompilerProcess() {
    if (isGradleDaemon) {
        destroyWithDescendants()
        return
    }

    if (supportsNormalTermination()) {
        destroy()
        return
    }

    /**
     * If we cannot terminate gracefully, then we try to destroy direct child processes (Gradle Wrapper)
     */
    children().asSequence().toList().forEach { child -> child.destroy() }
    destroy()
}
