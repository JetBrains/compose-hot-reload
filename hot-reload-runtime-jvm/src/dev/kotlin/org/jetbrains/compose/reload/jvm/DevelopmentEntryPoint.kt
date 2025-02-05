/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

@file:JvmName("JvmDevelopmentEntryPoint")

package org.jetbrains.compose.reload.jvm

import androidx.compose.runtime.*
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.update
import org.jetbrains.compose.reload.agent.orchestration
import org.jetbrains.compose.reload.agent.send
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.CleanCompositionRequest
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.RetryFailedCompositionRequest
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.UIException
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.UIRendered
import org.jetbrains.compose.reload.orchestration.asFlow

private val logger = createLogger()

@Composable
fun DevelopmentEntryPoint(child: @Composable () -> Unit) {
    /* Checking if we're currently in the stack of a hot reload */
    if (hotReloadStateLocal.current != null) {
        child()
        return
    }

    val windowId = startWindowManager()

    LaunchedEffect(Unit) {
        orchestration.asFlow().filterIsInstance<CleanCompositionRequest>().collect { value ->
            cleanComposition()
        }
    }

    LaunchedEffect(Unit) {
        orchestration.asFlow().filterIsInstance<RetryFailedCompositionRequest>().collect {
            retryFailedCompositions()
        }
    }

    /* Agent */
    val currentHotReloadState by hotReloadState.collectAsState()

    CompositionLocalProvider(hotReloadStateLocal provides currentHotReloadState) {
        key(currentHotReloadState.key) {
            logger.orchestration("Composing UI: $currentHotReloadState")
            runCatching { child() }.onFailure { exception ->
                logger.orchestration("Failed invoking 'JvmDevelopmentEntryPoint':", exception)

                /*
                UI-Exception: Nuke state captured in the UI by incrementing the key
                 */
                hotReloadState.update { state -> state.copy(key = state.key + 1, error = exception) }

                UIException(
                    windowId = windowId,
                    message = exception.message,
                    stacktrace = exception.stackTrace.toList()
                ).send()

            }.getOrThrow()
        }
    }


    /* Notify orchestration about the UI being rendered */
    UIRendered(
        windowId = windowId, reloadRequestId = currentHotReloadState.reloadRequestId, currentHotReloadState.iteration
    ).send()
}
