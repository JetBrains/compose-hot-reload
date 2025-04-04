/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.agent

import org.jetbrains.compose.reload.core.HotReloadEnvironment
import org.jetbrains.compose.reload.core.HotReloadProperty
import org.jetbrains.compose.reload.core.HotReloadProperty.DevToolsClasspath
import org.jetbrains.compose.reload.core.Os
import org.jetbrains.compose.reload.core.createLogger
import java.io.File
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.jvm.optionals.getOrNull

private val logger = createLogger()

internal fun launchDevtoolsApplication() {
    if (HotReloadEnvironment.isHeadless) return
    if (!HotReloadEnvironment.devToolsEnabled) return

    val classpath = HotReloadEnvironment.devToolsClasspath ?: error("Missing '${DevToolsClasspath}'")

    val java = resolveDevtoolsJavaBinary()
    val arguments = ProcessHandle.current().info().arguments().getOrNull()
        ?.takeIf { java == ProcessHandle.current().info().command().getOrNull() }
        ?: run { logger.error("Cannot find arguments in current process"); emptyArray<String>() }

    logger.info("Starting Dev Tools")

    val process = ProcessBuilder(
        java, "-cp", classpath.joinToString(File.pathSeparator),
        "-D${HotReloadProperty.OrchestrationPort.key}=${orchestration.port}",
        "-D${HotReloadProperty.GradleBuildContinuous.key}=${HotReloadEnvironment.gradleBuildContinuous}",
        "-D${HotReloadProperty.DevToolsTransparencyEnabled.key}=${HotReloadEnvironment.devToolsTransparencyEnabled}",
        "-Dapple.awt.UIElement=true",
        "org.jetbrains.compose.reload.jvm.tooling.Main",
        "--applicationCommand=$java",
        *arguments.map { arg -> "--applicationArg=$arg" }.toTypedArray()
    ).inheritIO().start()

    Runtime.getRuntime().addShutdownHook(Thread {
        process.destroy()
    })
}

private fun resolveDevtoolsJavaBinary(): String? {
    fun Path.resolveJavaHome(): Path = resolve(
        if (Os.currentOrNull() == Os.Windows) "bin/java.exe" else "bin/java"
    )

    System.getProperty("java.home")?.let { javaHome ->
        return Path(javaHome).resolveJavaHome().absolutePathString()
    }

    System.getenv("JAVA_HOME")?.let { javaHome ->
        return Path(javaHome).resolveJavaHome().absolutePathString()
    }

    return null
}
