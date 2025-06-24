/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.jvm

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import org.jetbrains.compose.reload.agent.orchestration
import org.jetbrains.compose.reload.agent.sendAsync
import org.jetbrains.compose.reload.core.WindowId
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.trace
import org.jetbrains.compose.reload.orchestration.OrchestrationClientRole
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ApplicationWindowPositioned
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ClientConnected
import org.jetbrains.compose.reload.orchestration.asFlow
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent


private val logger = createLogger()

@Composable
@Suppress("INVISIBLE_REFERENCE")
internal fun startWindowManager(): WindowId? {
    val window = androidx.compose.ui.window.LocalWindow.current ?: return null
    val windowId = remember { WindowId.create() }

    LaunchedEffect(windowId) {
        var isActive = true

        fun broadcastActiveState() {
            isActive = true
            ApplicationWindowPositioned(
                windowId, window.x, window.y, window.width, window.height, isAlwaysOnTop = window.isAlwaysOnTop
            ).sendAsync()
        }

        fun broadcastGone() {
            isActive = false
            OrchestrationMessage.ApplicationWindowGone(windowId).sendAsync()
        }

        broadcastActiveState()

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
            override fun componentHidden(e: ComponentEvent?) {
                logger.trace { "$windowId: componentHidden" }
                broadcastGone()
            }


            override fun componentShown(e: ComponentEvent?) {
                logger.trace { "$windowId: componentShown" }
                broadcastActiveState()
            }

            override fun componentResized(e: ComponentEvent?) {
                logger.trace { "$windowId: componentResized" }
                broadcastActiveState()
            }

            override fun componentMoved(e: ComponentEvent?) {
                logger.trace { "$windowId: componentMoved" }
                broadcastActiveState()
            }
        }

        window.addWindowListener(windowListener)
        window.addWindowStateListener(windowListener)
        window.addWindowFocusListener(windowListener)
        window.addComponentListener(componentListener)

        launch {
            orchestration.asFlow().filterIsInstance<ClientConnected>().collect { message ->
                if (message.clientRole == OrchestrationClientRole.Tooling && isActive) {
                    broadcastActiveState()
                }
            }
        }

        currentCoroutineContext().job.invokeOnCompletion {
            window.removeWindowListener(windowListener)
            window.removeComponentListener(componentListener)
            broadcastGone()
        }

        awaitCancellation()
    }

    return windowId
}
