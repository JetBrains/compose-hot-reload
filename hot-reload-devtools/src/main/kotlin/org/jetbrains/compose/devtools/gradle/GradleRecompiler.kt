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
import org.jetbrains.compose.reload.core.Os
import org.jetbrains.compose.reload.core.awaitOrThrow
import org.jetbrains.compose.reload.core.destroyWithDescendants
import org.jetbrains.compose.reload.core.error
import org.jetbrains.compose.reload.core.info
import org.jetbrains.compose.reload.core.subprocessDefaultArguments
import org.jetbrains.compose.reload.core.toFuture
import org.jetbrains.compose.reload.core.warn
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.deleteIfExists
import kotlin.io.path.isRegularFile
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.pathString
import kotlin.io.path.writeText
import kotlin.jvm.optionals.getOrNull
import kotlin.streams.asSequence

internal class GradleRecompiler(
    private val buildRoot: Path,
    private val buildProject: String,
    private val buildTask: String,
) : Recompiler {
    override val name: String = Gradle.name

    override suspend fun buildAndReload(context: RecompilerContext): ExitCode {
        val orchestrationPort = context.orchestration.port.awaitOrThrow()

        /* Check if there is another known Gradle Process running */
        val gradlePidFile = HotReloadEnvironment.pidFile?.let { pidFile ->
            pidFile.resolveSibling("${pidFile.nameWithoutExtension}.gradle.pid")
        }

        if (gradlePidFile != null && gradlePidFile.isRegularFile()) run kill@{
            val previousPid = gradlePidFile.toFile().readText().toLongOrNull() ?: return@kill
            val previousProcessHandle = ProcessHandle.of(previousPid).getOrNull() ?: return@kill
            context.logger.error("Previous Gradle process with pid '$previousPid' found; Waiting...")
            previousProcessHandle.onExit().toFuture().await()
        }

        val processBuilder = context.process {
            if (HotReloadEnvironment.gradleJavaHome == null) {
                context.logger.warn("Missing '${HotReloadProperty.GradleJavaHome}' property. Using system java")
            }

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
                    *subprocessDefaultArguments(BuildTool, orchestrationPort).toTypedArray(),
                    "-D${HotReloadProperty.GradleJavaHome.key}=${HotReloadEnvironment.gradleJavaHome?.pathString}"
                        .takeIf { HotReloadEnvironment.gradleJavaHome != null },

                    /* Continuous mode arguments */
                    "-t".takeIf { HotReloadEnvironment.gradleBuildContinuous },
                    "--no-daemon".takeIf { !isGradleDaemon },
                )
            )
        }

        val process = processBuilder.start()
        context.invokeOnDispose { process.toHandle().destroyGradleProcess() }
        context.logger.info("'Recompiler': Started (${process.pid()})")

        gradlePidFile?.writeText(process.pid().toString())
        process.onExit().whenComplete { _, _ -> gradlePidFile?.deleteIfExists() }

        withContext(Dispatchers.IO) {
            process.inputStream.bufferedReader().forEachLine { line ->
                context.logger.info(line)
            }
        }

        return ExitCode(process.onExit().toFuture().awaitOrThrow().exitValue())
    }

    private val isGradleDaemon = run {
        if (HotReloadEnvironment.buildSystem != Gradle) return@run false
        if (!HotReloadEnvironment.gradleBuildContinuous) return@run true
        when (HotReloadEnvironment.launchMode) {
            LaunchMode.Ide, LaunchMode.Detached -> true
            LaunchMode.GradleBlocking -> false
            null -> false
        }
    }

    private fun ProcessHandle.destroyGradleProcess() {
        try {
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
        } finally {
            onExit().get(5, TimeUnit.SECONDS)
        }
    }
}
