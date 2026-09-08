/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.jvm

import androidx.compose.runtime.CompositeKeyHashCode
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.window.WindowPlacement
import kotlinx.coroutines.flow.update
import org.jetbrains.compose.reload.agent.reloadCompositionState
import org.jetbrains.compose.reload.core.HotReloadEnvironment
import java.awt.Rectangle
import javax.swing.SwingUtilities
import javax.swing.Timer

/**
 * Resets the current composition in response to an explicit request (e.g. a 'CleanCompositionRequest'
 * or 'RetryFailedCompositionRequest').
 *
 * The strategy is selected by [HotReloadEnvironment.compositionHardResetEnabled]:
 *  - hard reset: a full teardown+rebuild via Compose's hot-reload state
 *    save/load ('reloadCompositionState'), which resets error states and
 *    recreates the scene/window. Required on Compose runtimes that cannot recover from
 *    a subcomposition error (See CMP-10459, CMP-10471).
 *  - light reset: bump 'hotReloadState.key', which only disposes and recomposes the entry point's
 *    subtree — no window recreation. Sufficient when Compose runtime can recover
 *    from subcomposition errors.
 */
internal fun resetComposition() {
    if (HotReloadEnvironment.compositionHardResetEnabled) {
        /*
         * CMP-10603: A hard reset tears the windows down and recreates them
         * (Compose 'saveStateAndDisposeForHotReload' clears the composition content, discarding all
         * remembered state, and 'loadStateAndComposeForHotReload' recomposes it from scratch).
         * A 'WindowState' created inside the composition (e.g. 'rememberWindowState()') is therefore
         * recreated with its default position/size, so the freshly created window no longer has the
         * position and size the user had made. Capture the current window bounds before the reset and
         * restore them onto the recreated windows after.
         */
        val bounds = onEventThreadBlocking { captureWindowBounds() }
        reloadCompositionState()
        onEventThreadBlocking { restoreWindowBounds(bounds) }
    } else {
        hotReloadState.update { state -> state.copy(key = state.key + 1) }
    }
}

private class WindowBounds(val normalBounds: Rectangle?, val placement: WindowPlacement)

/**
 * Captures the geometry and [WindowPlacement] of the windows the runtime currently manages (see
 * [activeWindows]), grouped by their composition position ([ManagedWindow.compositeKeyHashCode]) and
 * kept in registration order within each group.
 */
private fun captureWindowBounds(): Map<CompositeKeyHashCode, List<WindowBounds>> {
    val result = LinkedHashMap<CompositeKeyHashCode, MutableList<WindowBounds>>()
    for (managed in activeWindows()) {
        val window = managed.window as? ComposeWindow ?: continue
        result.getOrPut(managed.compositeKeyHashCode) { ArrayList() }
            .add(WindowBounds(managed.normalBounds, window.placement))
    }
    return result
}

/**
 * Re-applies the geometry captured by [captureWindowBounds] onto the (recreated) windows, matched
 * positionally within each composition position.
 *
 * The recreated window is first put into [WindowPlacement.Floating] with its normal bounds, so that
 * becomes the geometry it returns to when leaving a maximized/fullscreen placement.
 * After that we restore placement (maximized/fullscreen).
 */
private fun restoreWindowBounds(captured: Map<CompositeKeyHashCode, List<WindowBounds>>) {
    if (captured.isEmpty()) return
    val consumed = HashMap<CompositeKeyHashCode, Int>()
    for (managed in activeWindows()) {
        val window = managed.window as? ComposeWindow ?: continue
        val group = captured[managed.compositeKeyHashCode] ?: continue
        val index = consumed.getOrDefault(managed.compositeKeyHashCode, 0)
        if (index >= group.size) continue
        consumed[managed.compositeKeyHashCode] = index + 1
        val restored = group[index]
        val normalBounds = restored.normalBounds
        if (normalBounds != null) {
            window.placement = WindowPlacement.Floating
            window.bounds = normalBounds
        }
        if (restored.placement != WindowPlacement.Floating) {
            restorePlacement(window, restored.placement)
        }
    }
}

private const val placementRestoreDelayMs = 500

/**
 * Unfortunately, on macOS we can't apply maximized/fullscreen placement just after the window re-created:
 * the system does not allow it. On the other hand, it seems there is no event that we can listen that
 * will tell us that we can apply the placement: `windowOpened`, `componentShown` are too early events.
 *
 * So as a workaround, we simply delay before applying the placement.
 */
private fun restorePlacement(window: ComposeWindow, target: WindowPlacement) {
    Timer(placementRestoreDelayMs) {
        if (window.isShowing && window.placement != target) window.placement = target
    }.apply { isRepeats = false }.start()
}

private fun <T> onEventThreadBlocking(action: () -> T): T {
    if (SwingUtilities.isEventDispatchThread()) return action()
    var result: Result<T>? = null
    SwingUtilities.invokeAndWait { result = runCatching(action) }
    return result!!.getOrThrow()
}
