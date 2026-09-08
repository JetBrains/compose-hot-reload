/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.jvm

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositeKeyHashCode
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.currentCompositeKeyHashCode
import androidx.compose.runtime.remember
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.window.WindowPlacement
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.consumeAsFlow
import org.jetbrains.compose.devtools.api.WindowsState
import org.jetbrains.compose.devtools.api.WindowsState.WindowState
import org.jetbrains.compose.reload.agent.orchestration
import org.jetbrains.compose.reload.agent.sendAsync
import org.jetbrains.compose.reload.core.WindowId
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.trace
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ApplicationWindowPositioned
import java.awt.Frame
import java.awt.Rectangle
import java.awt.Window
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.beans.PropertyChangeListener
import java.lang.ref.WeakReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock


private val logger = createLogger()

internal class ManagedWindow(
    window: Window,
    val compositeKeyHashCode: CompositeKeyHashCode,
) {
    val windowId: WindowId = WindowId.create()

    private val windowReference = WeakReference(window)
    val window: Window? get() = windowReference.get()

    /**
     * The most recent bounds observed while the window was in its [WindowPlacement.Floating] state.
     */
    var normalBounds: Rectangle? = null
}

/**
 * The top-level windows currently managed by [startWindowManager], in registration order.
 */
private val activeWindowsLock = ReentrantLock()
private val activeWindows = ArrayList<ManagedWindow>()

internal fun activeWindows(): List<ManagedWindow> =
    activeWindowsLock.withLock { ArrayList(activeWindows) }

/**
 * Records the current bounds of [window] as its normal bounds, but only while it is genuinely floating.
 * We read Compose's [WindowPlacement] rather than AWT's 'Frame.extendedState' because on macOS a
 * maximized/fullscreen window (green button) keeps 'extendedState == NORMAL'.
 */
private fun recordNormalBounds(managed: ManagedWindow, window: Window) {
    if (window !is ComposeWindow) return
    if (window.placement != WindowPlacement.Floating || window.isMinimized) return
    managed.normalBounds = window.bounds
}

private fun registerWindow(compositeKeyHashCode: CompositeKeyHashCode, window: Window): ManagedWindow =
    activeWindowsLock.withLock {
        ManagedWindow(window, compositeKeyHashCode).also { activeWindows.add(it) }
    }

private fun unregisterWindow(managed: ManagedWindow) {
    activeWindowsLock.withLock { activeWindows.remove(managed) }
}

@Composable
internal fun startWindowManager(window: Window): WindowId {
    val compositeKeyHashCode = currentCompositeKeyHashCode
    val managed = remember { registerWindow(compositeKeyHashCode, window) }
    val windowId = managed.windowId

    remember(windowId) {
        val windowState = Channel<WindowState?>(Channel.CONFLATED)

        /* Synchronize windows state with orchestration */
        orchestration.subtask {
            windowState.consumeAsFlow().conflate().collect { state ->
                orchestration.update(WindowsState) { current ->
                    val windows = if (state == null) current.windows - windowId
                    else current.windows + (windowId to state)
                    WindowsState(windows)
                }
            }
        }

        fun broadcastActiveState() {
            recordNormalBounds(managed, window)

            windowState.trySendBlocking(
                WindowState(
                    x = window.x, y = window.y, width = window.width, height = window.height,
                    isAlwaysOnTop = window.isAlwaysOnTop,
                    title = (window as? Frame)?.title,
                )
            )

            ApplicationWindowPositioned(
                windowId, window.x, window.y, window.width, window.height, isAlwaysOnTop = window.isAlwaysOnTop
            ).sendAsync()
        }

        fun broadcastGone() {
            windowState.trySendBlocking(null)
            OrchestrationMessage.ApplicationWindowGone(windowId).sendAsync()
        }

        if (window.isVisible) {
            broadcastActiveState()
        }

        val windowListener = object : WindowAdapter() {
            override fun windowIconified(e: WindowEvent?) {
                logger.trace { "$windowId: $windowId: windowIconified" }
                broadcastGone()
            }

            override fun windowDeiconified(e: WindowEvent?) {
                logger.trace { "$windowId: windowDeiconified" }
                broadcastActiveState()
            }

            override fun windowClosed(e: WindowEvent?) {
                logger.trace { "$windowId: windowClosed" }
                broadcastGone()
            }

            override fun windowGainedFocus(e: WindowEvent?) {
                logger.trace { "$windowId: windowGainedFocus" }
                OrchestrationMessage.ApplicationWindowGainedFocus(windowId).sendAsync()
                super.windowGainedFocus(e)
            }

            override fun windowActivated(e: WindowEvent?) {
                logger.trace { "$windowId: windowActivated" }
                broadcastActiveState()
                super.windowActivated(e)
            }
        }

        val componentListener = object : ComponentAdapter() {
            fun broadcastIfActive() {
                /**
                 * `window.isActive` behaves inconsistently on Linux
                 * Therefore we use `window.isVisible` to check if the window is active
                 */
                if (window.isVisible) {
                    broadcastActiveState()
                }
            }

            override fun componentHidden(e: ComponentEvent?) {
                logger.trace { "$windowId: componentHidden" }
                broadcastGone()
            }

            override fun componentShown(e: ComponentEvent?) {
                logger.trace { "$windowId: componentShown" }
                broadcastIfActive()
            }

            override fun componentResized(e: ComponentEvent?) {
                logger.trace { "$windowId: componentResized" }
                broadcastIfActive()
            }

            override fun componentMoved(e: ComponentEvent?) {
                logger.trace { "$windowId: componentMoved" }
                broadcastIfActive()
            }
        }

        val titleListener = PropertyChangeListener { event ->
            logger.trace { "$windowId: title changed: '${event.oldValue}' -> '${event.newValue}'" }
            if (window.isVisible) broadcastActiveState()
        }

        window.addWindowListener(windowListener)
        window.addWindowStateListener(windowListener)
        window.addWindowFocusListener(windowListener)
        window.addComponentListener(componentListener)
        window.addPropertyChangeListener("title", titleListener)

        object : RememberObserver {
            override fun onRemembered() {}
            override fun onAbandoned() {}

            override fun onForgotten() {
                unregisterWindow(managed)
                window.removeWindowListener(windowListener)
                window.removeComponentListener(componentListener)
                window.removePropertyChangeListener("title", titleListener)
                broadcastGone()
                windowState.close()
            }
        }
    }
    return windowId
}
