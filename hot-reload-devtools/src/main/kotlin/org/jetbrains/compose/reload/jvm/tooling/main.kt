/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

@file:JvmName("Main")

package org.jetbrains.compose.reload.jvm.tooling

import io.sellmair.evas.Events
import io.sellmair.evas.States
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.jetbrains.compose.reload.jvm.tooling.states.launchConsoleLogState
import org.jetbrains.compose.reload.jvm.tooling.states.launchReloadState
import org.jetbrains.compose.reload.jvm.tooling.states.launchUIErrorState
import org.jetbrains.compose.reload.jvm.tooling.states.launchWindowsState

val applicationScope = CoroutineScope(Dispatchers.Main + SupervisorJob() + Events() + States())

fun main() {
    applicationScope.launchConsoleLogState()
    applicationScope.launchWindowsState()
    applicationScope.launchUIErrorState()
    applicationScope.launchReloadState()
    runDevToolingApplication()
}
