/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.devtools.gradle

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.devtools.api.Recompiler
import org.jetbrains.compose.devtools.api.RecompilerContext
import org.jetbrains.compose.reload.core.BuildSystem.Gradle
import org.jetbrains.compose.reload.core.ExitCode
import org.jetbrains.compose.reload.core.HotReloadEnvironment
import org.jetbrains.compose.reload.core.HotReloadProperty
import org.jetbrains.compose.reload.core.HotReloadProperty.Environment.BuildTool
import org.jetbrains.compose.reload.core.LaunchMode
import org.jetbrains.compose.reload.core.Logger.Level
import org.jetbrains.compose.reload.core.Os
import org.jetbrains.compose.reload.core.awaitOrThrow
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.debug
import org.jetbrains.compose.reload.core.destroyWithDescendants
import org.jetbrains.compose.reload.core.error
import org.jetbrains.compose.reload.core.runDirectoryLockFile
import org.jetbrains.compose.reload.core.subprocessSystemProperties
import org.jetbrains.compose.reload.core.toFuture
import org.jetbrains.compose.reload.core.warn
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.pathString
import kotlin.io.path.writeText
import kotlin.jvm.optionals.getOrNull

internal class GradleRecompiler(
    private val buildRoot: Path,
    private val buildProject: String,
    private val buildTask: String,
) : Recompiler {
    override val name: String = Gradle.name

    private val logger = createLogger()

    override suspend fun buildAndReload(context: RecompilerContext): ExitCode {
        val orchestrationPort = context.orchestration.port.awaitOrThrow()

        /* Check if there is another known Gradle Process running */
        val gradlePidFile = HotReloadEnvironment.pidFile?.let { pidFile ->
            pidFile.resolveSibling("${pidFile.nameWithoutExtension}.gradle.pid")
        }

        /*
        If we can find another process under the previous pid file:
        - warn the user if possible
        - wait for the previous build to finish
         */
        if (gradlePidFile != null && gradlePidFile.isRegularFile()) run kill@{
            val previousPid = runDirectoryLockFile?.withLock {
                gradlePidFile.toFile().readText().toLongOrNull()
            } ?: return@kill
            val previousProcessHandle = ProcessHandle.of(previousPid).getOrNull() ?: return@kill
            context.logger.error("Previous Gradle process with pid '$previousPid' found; Waiting...")
            previousProcessHandle.onExit().toFuture().await()
        }

        /*
        Launch the build, creating a new process.
         */
        val processBuilder = context.process {
            /* Setup JAVA_HOME: Prefer the java home of the original Gradle compilation */
            if (HotReloadEnvironment.gradleJavaHome == null) {
                context.logger.warn("Missing '${HotReloadProperty.GradleJavaHome}' property. Using system java")
            }

            val javaHome = HotReloadEnvironment.gradleJavaHome
                ?: System.getProperty("java.home")?.let(::Path)?.takeIf { it.exists() }

            if (javaHome != null) {
                environment()["JAVA_HOME"] = javaHome.pathString
            }

            /* Setup gradle wrapper command */
            val gradleScriptCommand = if (Os.currentOrNull() == Os.Windows) arrayOf("cmd", "/c", "gradlew.bat")
            else arrayOf("./gradlew")

            val gradleTaskPath = if (buildProject == ":") ":$buildTask"
            else "$buildProject:$buildTask"

            directory(buildRoot.toFile())
            redirectErrorStream(true)
            command(
                listOfNotNull(
                    *gradleScriptCommand,
                    gradleTaskPath,
                    "--console=plain",

                    "-D${HotReloadProperty.IsHotReloadBuild.key}=true",
                    "-P${HotReloadProperty.IsHotReloadBuild.key}=true",
                    *subprocessSystemProperties(BuildTool, orchestrationPort).toTypedArray(),
                    "-D${HotReloadProperty.GradleJavaHome.key}=${HotReloadEnvironment.gradleJavaHome?.pathString}"
                        .takeIf { HotReloadEnvironment.gradleJavaHome != null },

                    /* Continuous mode arguments */
                    "-t".takeIf { HotReloadEnvironment.gradleBuildContinuous },
                    "--no-daemon".takeIf { !useGradleDaemon },
                )
            )
        }

        /* Start the process and wire up the disposal */
        val process = processBuilder.start()
        context.invokeOnDispose { process.toHandle().destroyGradleProcess() }
        context.logger.debug("'Recompiler': Started (${process.pid()})")

        /* Write the pid file with the new process id */
        runDirectoryLockFile?.withLock {
            gradlePidFile?.writeText(process.pid().toString())
        }

        process.onExit().whenComplete { _, _ -> gradlePidFile?.deleteIfExists() }

        /* Read the output of the new process and forward it as log messages */
        withContext(Dispatchers.IO) {
            process.inputStream.bufferedReader().forEachLine { line ->
                val level = when {
                    line.startsWith("e: ") -> Level.Error
                    line.startsWith("warning: ") -> Level.Warn
                    line.startsWith("w: ") -> Level.Warn
                    line.startsWith(">") -> Level.Debug
                    else -> Level.Info
                }
                context.logger.log(level, line)
            }
        }

        return ExitCode(process.onExit().toFuture().awaitOrThrow().exitValue())
    }

    private fun ProcessHandle.destroyGradleProcess() {
        if (!destroyWithDescendants()) {
            logger.error("'Recompiler': 'destroyWithDescendants' did not finish successfully")
        }
    }
}

private val useGradleDaemon = run {
    if (HotReloadEnvironment.buildSystem != Gradle) return@run false
    if (!HotReloadEnvironment.gradleBuildContinuous) return@run true
    when (HotReloadEnvironment.launchMode) {
        LaunchMode.Ide, LaunchMode.Detached -> true
        LaunchMode.GradleBlocking -> false
        null -> false
    }
}
