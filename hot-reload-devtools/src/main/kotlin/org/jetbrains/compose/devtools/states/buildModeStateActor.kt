/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.devtools.states

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.devtools.api.BuildModeState
import org.jetbrains.compose.devtools.orchestration
import org.jetbrains.compose.reload.DelicateHotReloadApi
import org.jetbrains.compose.reload.core.HotReloadEnvironment

/**
 * Publishes the recompiler's build mode so tooling clients (e.g. the MCP server) running in a
 * separate process can discover it. The mode is fixed at startup and never changes, so this is a
 * one-shot publish.
 */
@OptIn(DelicateHotReloadApi::class)
fun CoroutineScope.launchBuildModeStateActor() = launch {
    orchestration.update(BuildModeState.Key) {
        BuildModeState(isContinuous = HotReloadEnvironment.gradleBuildContinuous)
    }
}
