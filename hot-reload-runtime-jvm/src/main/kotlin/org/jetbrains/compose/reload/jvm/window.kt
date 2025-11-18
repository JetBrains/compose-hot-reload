/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.jvm

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.job
import org.jetbrains.compose.devtools.api.WindowsState
import org.jetbrains.compose.devtools.api.WindowsState.WindowState
import org.jetbrains.compose.reload.agent.orchestration
import org.jetbrains.compose.reload.agent.sendAsync
import org.jetbrains.compose.reload.core.WindowId
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.trace
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ApplicationWindowPositioned
import java.awt.Window
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent


private val logger = createLogger()

@Composable
internal fun startWindowManager(window: Window): WindowId {
    val windowId = remember { WindowId.create() }

    LaunchedEffect(windowId) {
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
            windowState.trySendBlocking(
                WindowState(
                    x = window.x, y = window.y, width = window.width, height = window.height,
                    isAlwaysOnTop = window.isAlwaysOnTop
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

        window.addWindowListener(windowListener)
        window.addWindowStateListener(windowListener)
        window.addWindowFocusListener(windowListener)
        window.addComponentListener(componentListener)

        currentCoroutineContext().job.invokeOnCompletion {
            window.removeWindowListener(windowListener)
            window.removeComponentListener(componentListener)
            broadcastGone()
            windowState.close()
        }

        awaitCancellation()
    }

    return windowId
}
