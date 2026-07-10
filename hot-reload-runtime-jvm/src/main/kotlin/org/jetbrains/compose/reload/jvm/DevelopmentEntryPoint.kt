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
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.update
import org.jetbrains.compose.devtools.api.UIErrorState
import org.jetbrains.compose.reload.InternalHotReloadApi
import org.jetbrains.compose.reload.agent.orchestration
import org.jetbrains.compose.reload.agent.sendAsync
import org.jetbrains.compose.reload.core.HotReloadEnvironment.reloadEffectsEnabled
import org.jetbrains.compose.reload.core.WindowId
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.launchTask
import org.jetbrains.compose.reload.core.debug
import org.jetbrains.compose.reload.core.error
import org.jetbrains.compose.reload.core.warn
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.CleanCompositionRequest
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.RetryFailedCompositionRequest
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ScreenshotRequest
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.SemanticTreeRequest
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.UIActionRequest
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.UIException
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.WindowResizeRequest
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.UIRendered
import org.jetbrains.compose.reload.orchestration.asFlow
import java.awt.Window

private val logger = createLogger()

@Composable
@InternalHotReloadApi
public fun DevelopmentEntryPoint(
    window: Window? = null,
    child: @Composable () -> Unit
) {

    /* Checking if we're currently in the stack of a hot reload */
    if (hotReloadStateLocal.current != null) {
        logger.warn(
            "Skipping 'DevelopmentEntryPoint': We're already in an entry point",
            Exception("Nested 'DevelopmentEntryPoint'")
        )
        child()
        return
    }

    val windowId = window?.let { startWindowManager(window) }

    LaunchedEffect(Unit) {
        orchestration.asFlow().filterIsInstance<CleanCompositionRequest>().collect { value ->
            resetComposition()
        }
    }

    LaunchedEffect(Unit) {
        orchestration.asFlow().filterIsInstance<RetryFailedCompositionRequest>().collect {
            resetComposition()
        }
    }

    // CMP-10248: use error state API to detect UI errors for Compose 1.11+.
    // Use runCatching() workaround below for older Compose.
    if (isRecomposerErrorStateApiAvailable) LaunchedEffect(windowId) {
        var reported: Throwable? = null
        recomposerErrors().collect { error ->
            when {
                // A new error detected by the recomposer
                error != null && error !== reported -> {
                    logger.error("UI exception:", error)
                    reported = error
                    publishUIError(windowId, error)
                }

                // The recomposer recovered: clear the overlay
                error == null && reported != null -> {
                    unpublishUIError(windowId)
                    reported = null
                }
            }
        }
    }

    if (window != null) {
        handleWindowRequests<ScreenshotRequest>(windowId, { it.windowId }) { handleScreenshotRequest(it, window, windowId) }
        handleWindowRequests<SemanticTreeRequest>(windowId, { it.windowId }) { handleSemanticTreeRequest(it, window, windowId) }
        handleWindowRequests<UIActionRequest>(windowId, { it.windowId }) { handleUIActionRequest(it, window, windowId) }
        handleWindowRequests<WindowResizeRequest>(windowId, { it.windowId }) { handleWindowResizeRequest(it, window) }
    }

    /* Agent */
    val currentHotReloadState by hotReloadState.collectAsState()

    val intercepted: @Composable () -> Unit = {
        logger.debug("Composing UI: $currentHotReloadState")
        @Suppress("ILLEGAL_RUN_CATCHING_AROUND_COMPOSABLE")
        runCatching {
            when {
                reloadEffectsEnabled -> ReloadEffects(child)
                else -> child()
            }
        }.onFailure { exception ->
            logger.error("Failed invoking 'JvmDevelopmentEntryPoint':", exception)
            if (!isRecomposerErrorStateApiAvailable) publishUIError(windowId, exception)
        }.onSuccess {
            if (!isRecomposerErrorStateApiAvailable) unpublishUIError(windowId)
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

/**
 * Installs a [LaunchedEffect] that handles incoming [T] requests targeting this window: those whose
 * [windowIdOf] is null (any window) or equal to [windowId]. The [respond] result is sent back over
 * orchestration.
 */
@Composable
private inline fun <reified T : OrchestrationMessage> handleWindowRequests(
    windowId: WindowId?,
    crossinline windowIdOf: (T) -> WindowId?,
    crossinline respond: (T) -> OrchestrationMessage,
) {
    LaunchedEffect(Unit) {
        orchestration.asFlow()
            .filterIsInstance<T>()
            .filter { request -> windowIdOf(request) == null || windowIdOf(request) == windowId }
            .collect { request -> respond(request).sendAsync() }
    }
}

/**
 * Publishes [exception] to the devtools error overlay (the per-window [UIErrorState]) and broadcasts a
 * [UIException] so the devtools error overlay can show its stack trace.
 */
private fun publishUIError(windowId: WindowId?, exception: Throwable) {
    if (windowId != null) updateUIErrorState { errors ->
        errors + (windowId to UIErrorState.UIError(
            message = exception.message,
            stacktrace = exception.stackTrace.map { it.toString() },
        ))
    }
    UIException(
        windowId = windowId,
        message = exception.message,
        stacktrace = exception.stackTrace.toList()
    ).sendAsync()
}

/** Removes the [windowId] entry from the overlay ([UIErrorState]). */
private fun unpublishUIError(windowId: WindowId?) {
    if (windowId != null) updateUIErrorState { errors -> errors - windowId }
}

private fun updateUIErrorState(
    update: (Map<WindowId, UIErrorState.UIError>) -> Map<WindowId, UIErrorState.UIError>
) {
    launchTask("update UIErrorState") {
        orchestration.update(UIErrorState) { current -> UIErrorState(update(current.errors)) }
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
        DevelopmentEntryPoint(window = window) {
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
        DevelopmentEntryPoint(window = window) {
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
        DevelopmentEntryPoint(window = window) {
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
        DevelopmentEntryPoint(window = window) {
            content()
        }
    }
}
