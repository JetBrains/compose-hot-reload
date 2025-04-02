/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.agent

import org.jetbrains.compose.reload.core.BuildSystem
import org.jetbrains.compose.reload.core.BuildSystem.Amper
import org.jetbrains.compose.reload.core.BuildSystem.Gradle
import org.jetbrains.compose.reload.core.HotReloadEnvironment
import org.jetbrains.compose.reload.core.HotReloadProperty
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.destroyWithDescendants
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.LogMessage
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.LogMessage.Companion.TAG_COMPILER
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.RecompileRequest
import org.jetbrains.compose.reload.orchestration.invokeWhenReceived
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.io.path.pathString

private val logger = createLogger()

private val buildSystem: BuildSystem = HotReloadEnvironment.buildSystem

private val gradleBuildRoot: String? = HotReloadEnvironment.gradleBuildRoot
private val gradleBuildProject: String? = HotReloadEnvironment.gradleBuildProject
private val gradleBuildTask: String? = HotReloadEnvironment.gradleBuildTask

private val amperBuildRoot: String? = HotReloadEnvironment.amperBuildRoot
private val amperBuildTask: String? = HotReloadEnvironment.amperBuildTask

private val recompileRequests = LinkedBlockingQueue<RecompileRequest>(
    /*
    Starting with one initial recompile request.

    For continuous builds:
     This request is the start signal, starting a single continuous build for Gradle.

    For non-continuous builds:
      This will warm up the recompiler.
      For Gradle, in particular, the tracking of the classpath
      is incremental; this means that the file collection has to be built initially (with this request)
     */
    listOf(RecompileRequest())
)

internal fun launchRecompiler() {
    val port = orchestration.port
    logger.debug("'Compose Recompiler': Using orchestration at '$port'")

    val processBuilder = when (buildSystem) {
        Amper -> {
            val amperBuildRoot = amperBuildRoot ?: run {
                logger.error("Missing '${HotReloadProperty.AmperBuildRoot.key}' property")
                return
            }

            val amperBuildTask = amperBuildTask ?: run {
                logger.error("Missing '${HotReloadProperty.AmperBuildTask.key}' property")
                return
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
                return
            }

            val gradleBuildProject = gradleBuildProject ?: run {
                logger.error("Missing '${HotReloadProperty.GradleBuildProject.key}' property")
                return
            }

            val gradleBuildTask = gradleBuildTask ?: run {
                logger.error("Missing '${HotReloadProperty.GradleBuildTask.key}' property")
                return
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
        logger.debug("'Recompiler' started")

        try {
            /*
            On continuous builds, the Gradle daemon will send the 'ready' signal once started up
            and listening for changes
            */
            if (!HotReloadEnvironment.gradleBuildContinuous) {
                OrchestrationMessage.RecompilerReady().send()
            }

            while (true) {
                val requests = takeRecompileRequests()
                val exitCode = processBuilder.startRecompilerProcess()
                requests.forEach { request ->
                    OrchestrationMessage.RecompileResult(
                        recompileRequestId = request.messageId,
                        exitCode = exitCode
                    ).send()
                }
            }
        } catch (_: InterruptedException) {
            logger.debug("'Recompiler': Interrupted: Shutting down")
        }
    }

    orchestration.invokeWhenReceived<RecompileRequest> { value ->
        recompileRequests.put(value)
    }

    orchestration.invokeWhenClosed {
        logger.debug("'Compose Recompiler': Sending close signal")
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
    val process: Process = start()
    val shutdownHook = thread(start = false) {
        logger.debug("'Recompiler': Destroying process (Shutdown)")
        process.destroyWithDescendants()
    }

    Runtime.getRuntime().addShutdownHook(shutdownHook)

    thread(name = "Recompiler Output", isDaemon = true) {
        process.inputStream.bufferedReader().use { reader ->
            while (true) {
                val nextLine = reader.readLine() ?: break
                logger.debug("'Compose Recompiler' output: $nextLine")
                LogMessage(TAG_COMPILER, nextLine).send()
            }
        }
    }

    val exitCode = try {
        process.waitFor()
    } catch (_: InterruptedException) {
        logger.debug("'Recompiler': Destroying process")
        process.destroyWithDescendants()
        if (!process.waitFor(15, TimeUnit.SECONDS)) {
            logger.debug("'Recompiler': Force destroying process (Interrupt)")
            process.destroyForcibly()
        }
        null
    }


    logger.debug("Recompile finished '$exitCode'")
    runCatching { Runtime.getRuntime().removeShutdownHook(shutdownHook) }
    return exitCode
}

private fun createRecompilerProcessBuilder(
    gradleBuildRoot: String,
    gradleBuildProject: String,
    gradleBuildTask: String,
    orchestrationPort: Int
): ProcessBuilder {
    return ProcessBuilder().directory(File(gradleBuildRoot))
        .command(
            createRecompilerGradleCommandLineArgs(
                gradleBuildProject = gradleBuildProject,
                gradleBuildTask = gradleBuildTask,
                orchestrationPort = orchestrationPort
            )
        )
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

    val gradleScriptCommand = if ("win" in System.getProperty("os.name").lowercase()) {
        arrayOf("cmd", "/c", "gradlew.bat")
    } else {
        arrayOf("./gradlew")
    }

    val gradleTaskPath = if (gradleBuildProject == ":") ":$gradleBuildTask"
    else "$gradleBuildProject:$gradleBuildTask"

    return listOfNotNull(
        *gradleScriptCommand,
        gradleTaskPath,
        "--console=plain",

        "-D${HotReloadProperty.OrchestrationPort.key}=$orchestrationPort",

        "-D${HotReloadProperty.GradleJavaHome.key}=${HotReloadEnvironment.gradleJavaHome?.pathString}"
            .takeIf { HotReloadEnvironment.gradleJavaHome != null },

        /* Continuous mode arguments */
        "-t".takeIf { HotReloadEnvironment.gradleBuildContinuous },
        "--no-daemon".takeIf { HotReloadEnvironment.gradleBuildContinuous },
    )
}

private fun createRecompilerAmperCommandLineArgs(amperBuildTask: String): List<String> {
    val amperScriptCommand = if ("win" in System.getProperty("os.name").lowercase()) {
        arrayOf("cmd", "/c", "amper.bat")
    } else {
        arrayOf("./amper")
    }

    return listOfNotNull(
        *amperScriptCommand,
        "task",
        amperBuildTask,
    )
}
