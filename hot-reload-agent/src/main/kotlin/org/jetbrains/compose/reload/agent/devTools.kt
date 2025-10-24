/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.agent

import org.jetbrains.compose.reload.core.Environment
import org.jetbrains.compose.reload.core.HotReloadEnvironment
import org.jetbrains.compose.reload.core.HotReloadEnvironment.devToolsDetached
import org.jetbrains.compose.reload.core.HotReloadProperty
import org.jetbrains.compose.reload.core.HotReloadProperty.Environment.DevTools
import org.jetbrains.compose.reload.core.JavaHome
import org.jetbrains.compose.reload.core.Os
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.debug
import org.jetbrains.compose.reload.core.error
import org.jetbrains.compose.reload.core.issueNewDebugSessionJvmArguments
import org.jetbrains.compose.reload.core.subprocessSystemProperties
import org.jetbrains.compose.reload.core.withHotReloadEnvironmentVariables
import java.io.File
import kotlin.concurrent.thread
import kotlin.io.path.absolutePathString

private val logger = createLogger()

internal class DevToolsHandle(
    val orchestrationPort: Int,
)

internal val devTools: DevToolsHandle? by lazy {
    tryStartDevToolsProcess()
}

internal fun startDevTools() {
    devTools
}

private fun tryStartDevToolsProcess(): DevToolsHandle? {
    if (!HotReloadEnvironment.devToolsEnabled) return null
    val classpath = HotReloadEnvironment.devToolsClasspath ?: return null
    logger.debug("Starting 'DevTools'")

    val process = ProcessBuilder(
        resolveDevtoolsJavaBinary(),
        *platformSpecificJvmArguments(),
        "-XX:+UseZGC", "-Xmx256M", "-XX:SoftMaxHeapSize=128M",
        "--enable-native-access=ALL-UNNAMED",
        "-cp", classpath.joinToString(File.pathSeparator),
        *subprocessSystemProperties(DevTools).toTypedArray(),
        *issueNewDebugSessionJvmArguments("DevTools"),
        "org.jetbrains.compose.devtools.Main",
    )
        .withHotReloadEnvironmentVariables(DevTools)
        .start()

    thread(name = "DevTools: Stderr", isDaemon = true) {
        val stderrLogger = createLogger("DevTools: Stderr", environment = Environment.devTools)
        process.errorStream.bufferedReader().forEachLine { line ->
            stderrLogger.error(line)
        }
    }

    /*
    Receive the orchestration port from the devtools process.
    Usually, the devtools is hosting the orchestration server, sending the port through stdout as
    'compose.reload.orchestration.port=2411'
     */
    val orchestrationPort = process.inputStream.bufferedReader().lineSequence()
        .filter { it.startsWith(HotReloadProperty.OrchestrationPort.key) }
        .first().substringAfter("=").trim().toInt()

    return DevToolsHandle(orchestrationPort)
}

private fun resolveDevtoolsJavaBinary(): String {
    return JavaHome.current().javaExecutable.absolutePathString()
}

private fun platformSpecificJvmArguments(): Array<String> = when (Os.current()) {
    // Allow reflective access to X11 classes for Linux
    // Required to properly set the app name in the taskbar
    Os.Linux -> arrayOf("--add-opens=java.desktop/sun.awt.X11=ALL-UNNAMED")
    // Disable dock icon when not running in detached mode for MacOS
    Os.MacOs -> arrayOf(
        "-Dapple.awt.UIElement=${!devToolsDetached}",
        "-Dapple.awt.application.name=Compose Hot Reload Dev Tools",
    )
    // We need no enable OpenGL rendering for skiko, because
    // DirectX rendering has issues with managing transparency/opacity
    // and click-through
    Os.Windows -> arrayOf("-Dskiko.renderApi=OPENGL")
}
