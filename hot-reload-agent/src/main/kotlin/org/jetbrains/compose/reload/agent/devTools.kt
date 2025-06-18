/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.agent

import org.jetbrains.compose.reload.core.Environment
import org.jetbrains.compose.reload.core.HotReloadEnvironment
import org.jetbrains.compose.reload.core.HotReloadProperty.DevToolsClasspath
import org.jetbrains.compose.reload.core.HotReloadProperty.Environment.DevTools
import org.jetbrains.compose.reload.core.Os
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.error
import org.jetbrains.compose.reload.core.getBlocking
import org.jetbrains.compose.reload.core.getOrThrow
import org.jetbrains.compose.reload.core.info
import org.jetbrains.compose.reload.core.issueNewDebugSessionJvmArguments
import org.jetbrains.compose.reload.core.subprocessDefaultArguments
import org.jetbrains.compose.reload.core.withHotReloadEnvironmentVariables
import java.io.File
import java.nio.file.Path
import kotlin.concurrent.thread
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString

private val logger = createLogger()

internal fun launchDevtoolsApplication() {
    if (!HotReloadEnvironment.devToolsEnabled) return
    val classpath = HotReloadEnvironment.devToolsClasspath ?: error("Missing '${DevToolsClasspath}'")
    logger.info("Starting 'DevTools'")

    val process = ProcessBuilder(
        resolveDevtoolsJavaBinary(), "-cp", classpath.joinToString(File.pathSeparator),
        *subprocessDefaultArguments(DevTools, orchestration.port.getBlocking().getOrThrow()).toTypedArray(),
        *issueNewDebugSessionJvmArguments("DevTools"),
        "-Dapple.awt.UIElement=true",
        "org.jetbrains.compose.devtools.Main",
    ).withHotReloadEnvironmentVariables(DevTools)
        .start()

    thread(name = "DevTools: Stderr", isDaemon = true) {
        val stderrLogger = createLogger("DevTools: Stderr", environment = Environment.devTools)
        process.errorStream.bufferedReader().forEachLine { line ->
            stderrLogger.error(line)
        }
    }
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
