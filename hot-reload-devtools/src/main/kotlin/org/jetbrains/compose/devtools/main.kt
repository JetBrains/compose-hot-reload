/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

@file:JvmName("Main")

package org.jetbrains.compose.devtools

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.window.WindowState
import io.sellmair.evas.Events
import io.sellmair.evas.States
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.jetbrains.compose.devtools.states.launchConsoleLogUIState
import org.jetbrains.compose.devtools.states.launchErrorUIState
import org.jetbrains.compose.devtools.states.launchNotificationsUIState
import org.jetbrains.compose.devtools.states.launchReloadCountStateActor
import org.jetbrains.compose.devtools.states.launchReloadCountUIState
import org.jetbrains.compose.devtools.states.launchReloadStateActor
import org.jetbrains.compose.devtools.states.launchReloadUIState
import org.jetbrains.compose.devtools.states.launchWindowsUIState
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.debug

internal val applicationScope = CoroutineScope(Dispatchers.Main + SupervisorJob() + Events() + States())

private val logger = createLogger()

/**
 * The associated [WindowState] of the target application (aka, the application under hot-reload, which we're
 * providing tooling for
 */
internal val targetApplicationWindowStateLocal = staticCompositionLocalOf<WindowState?> { null }

internal fun CoroutineScope.launchApplicationStates() {
    launchConsoleLogUIState()
    launchWindowsUIState()
    launchErrorUIState()

    launchReloadCountStateActor()
    launchReloadCountUIState()

    launchReloadStateActor()
    launchReloadUIState()

    launchRestartActor()
    launchNotificationsUIState()
}

fun main() {
    logger.debug("PID: '${ProcessHandle.current().pid()}'")
    logger.debug("Starting devtools process")
    setupDevToolsProcess()

    logger.debug("Starting application states")
    applicationScope.launchApplicationStates()
    launchRecompiler()

    logger.debug("Starting UI")
    startDevToolsUI()
}
