/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

@file:JvmName("JvmDevelopmentEntryPoint")

package org.jetbrains.compose.reload.jvm

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.awt.ComposeDialog
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.window.DialogWindowScope
import androidx.compose.ui.window.FrameWindowScope
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.update
import org.jetbrains.compose.reload.agent.orchestration
import org.jetbrains.compose.reload.agent.sendAsync
import org.jetbrains.compose.reload.agent.sendBlocking
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.error
import org.jetbrains.compose.reload.core.info
import org.jetbrains.compose.reload.core.warn
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
        logger.warn(
            "Skipping 'DevelopmentEntryPoint': We're already in an entry point",
            Exception("Nested 'DevelopmentEntryPoint'")
        )
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

    val intercepted: @Composable () -> Unit = {
        logger.info("Composing UI: $currentHotReloadState")
        runCatching { child() }.onFailure { exception ->
            logger.error("Failed invoking 'JvmDevelopmentEntryPoint':", exception)
            hotReloadState.update { state -> state.copy(uiError = exception) }
            UIException(
                windowId = windowId,
                message = exception.message,
                stacktrace = exception.stackTrace.toList()
            ).sendBlocking()

        }.onSuccess {
            hotReloadState.update { state -> state.copy(uiError = null) }
            UIRendered(
                windowId = windowId,
                reloadRequestId = currentHotReloadState.reloadRequestId,
                currentHotReloadState.iteration
            ).sendAsync()
        }.getOrThrow()
    }

    CompositionLocalProvider(hotReloadStateLocal provides currentHotReloadState) {
        key(currentHotReloadState.key) {
            intercepted()
        }
    }
}


@Suppress("unused", "EXTENSION_SHADOWED_BY_MEMBER") // used by instrumentation
@OptIn(ExperimentalComposeUiApi::class)
@PublishedApi
internal fun ComposeWindow.setContent(
    onPreviewKeyEvent: (KeyEvent) -> Boolean,
    onKeyEvent: (KeyEvent) -> Boolean,
    content: @Composable FrameWindowScope.() -> Unit
) {
    setContent(onPreviewKeyEvent = onPreviewKeyEvent, onKeyEvent = onKeyEvent) {
        DevelopmentEntryPoint {
            content()
        }
    }
}

@Suppress("unused", "EXTENSION_SHADOWED_BY_MEMBER") // used by instrumentation
@OptIn(ExperimentalComposeUiApi::class)
@PublishedApi
internal fun ComposeWindow.setContent(
    content: @Composable FrameWindowScope.() -> Unit
) {
    setContent {
        DevelopmentEntryPoint {
            content()
        }
    }
}

@Suppress("unused", "EXTENSION_SHADOWED_BY_MEMBER") // used by instrumentation
@OptIn(ExperimentalComposeUiApi::class)
@PublishedApi
internal fun ComposeDialog.setContent(
    onPreviewKeyEvent: (KeyEvent) -> Boolean,
    onKeyEvent: (KeyEvent) -> Boolean,
    content: @Composable DialogWindowScope.() -> Unit
) {
    setContent(onPreviewKeyEvent = onPreviewKeyEvent, onKeyEvent = onKeyEvent) {
        DevelopmentEntryPoint {
            content()
        }
    }
}

@Suppress("unused", "EXTENSION_SHADOWED_BY_MEMBER") // used by instrumentation
@OptIn(ExperimentalComposeUiApi::class)
@PublishedApi
internal fun ComposeDialog.setContent(
    content: @Composable DialogWindowScope.() -> Unit
) {
    setContent {
        DevelopmentEntryPoint {
            content()
        }
    }
}
