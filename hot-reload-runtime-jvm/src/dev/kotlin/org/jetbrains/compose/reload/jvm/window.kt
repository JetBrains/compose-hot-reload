/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.jvm

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import org.jetbrains.compose.reload.agent.orchestration
import org.jetbrains.compose.reload.agent.send
import org.jetbrains.compose.reload.core.WindowId
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ApplicationWindowPositioned
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ClientConnected
import org.jetbrains.compose.reload.orchestration.asFlow
import java.awt.Window
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.util.WeakHashMap

private val windowsIds = WeakHashMap<Window, WindowId>()

private val logger = createLogger()

@Composable
@Suppress("INVISIBLE_REFERENCE")
internal fun startWindowManager(): WindowId? {
    val window = androidx.compose.ui.window.LocalWindow.current
    val windowId = if (window != null) windowsIds.getOrPut(window) { WindowId.create() } else null

    LaunchedEffect(windowId) {
        if (window == null || windowId == null) return@LaunchedEffect

        fun broadcastWindowPosition() {
            ApplicationWindowPositioned(
                windowId, window.x, window.y, window.width, window.height, isAlwaysOnTop = window.isAlwaysOnTop
            ).send()
        }

        broadcastWindowPosition()

        val windowListener = object : WindowAdapter() {
            override fun windowIconified(e: WindowEvent?) {
                logger.debug("$windowId: $windowId: windowIconified")
                OrchestrationMessage.ApplicationWindowGone(windowId).send()
            }

            override fun windowDeiconified(e: WindowEvent?) {
                logger.debug("$windowId: windowDeiconified")
                broadcastWindowPosition()
            }

            override fun windowClosed(e: WindowEvent?) {
                logger.debug("$windowId: windowClosed")
                OrchestrationMessage.ApplicationWindowGone(windowId).send()
            }

            override fun windowGainedFocus(e: WindowEvent?) {
                logger.debug("$windowId: windowGainedFocus")
                OrchestrationMessage.ApplicationWindowGainedFocus(windowId).send()
                super.windowGainedFocus(e)
            }

            override fun windowActivated(e: WindowEvent?) {
                logger.debug("$windowId: windowActivated")
                broadcastWindowPosition()
                super.windowActivated(e)
            }
        }

        val componentListener = object : ComponentAdapter() {
            override fun componentHidden(e: ComponentEvent?) {
                logger.debug("$windowId: componentHidden")
                OrchestrationMessage.ApplicationWindowGone(windowId).send()
            }


            override fun componentShown(e: ComponentEvent?) {
                logger.debug("$windowId: componentShown")
                broadcastWindowPosition()
            }

            override fun componentResized(e: ComponentEvent?) {
                logger.debug("$windowId: componentResized")
                broadcastWindowPosition()
            }

            override fun componentMoved(e: ComponentEvent?) {
                logger.debug("$windowId: componentMoved")
                broadcastWindowPosition()
            }
        }

        window.addWindowListener(windowListener)
        window.addWindowStateListener(windowListener)
        window.addWindowFocusListener(windowListener)
        window.addComponentListener(componentListener)

        launch {
            orchestration.asFlow().filterIsInstance<ClientConnected>().collect { message ->
                broadcastWindowPosition()
            }
        }

        currentCoroutineContext().job.invokeOnCompletion {
            window.removeWindowListener(windowListener)
            window.removeComponentListener(componentListener)
        }

        awaitCancellation()
    }

    return windowId
}
