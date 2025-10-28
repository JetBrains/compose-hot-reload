package org.jetbrains.compose.devtools.sidecar

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.awt.ComposeDialog
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.DialogWindowScope
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.rememberDialogState
import org.jetbrains.compose.devtools.orchestration
import org.jetbrains.compose.devtools.sendBlocking
import org.jetbrains.compose.devtools.theme.DtPadding
import org.jetbrains.compose.devtools.theme.DtSizes
import org.jetbrains.compose.devtools.theme.DtTitles.DEV_TOOLS
import org.jetbrains.compose.reload.core.HotReloadEnvironment.devToolsTransparencyEnabled
import org.jetbrains.compose.reload.core.Os
import org.jetbrains.compose.reload.core.Try
import org.jetbrains.compose.reload.core.WindowId
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.debug
import org.jetbrains.compose.reload.core.getOrNull
import org.jetbrains.compose.reload.core.leftOr
import org.jetbrains.compose.reload.core.warn
import org.jetbrains.compose.reload.core.withType
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ApplicationWindowGainedFocus
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ShutdownRequest
import java.awt.GraphicsDevice
import java.awt.GraphicsEnvironment
import java.awt.Point
import kotlin.system.exitProcess

private val logger = createLogger()

private val transparencySupported = transparencySupported()

internal val devToolsUseTransparency = (devToolsTransparencyEnabled && transparencySupported).also {
    if (devToolsTransparencyEnabled && !transparencySupported) {
        logger.warn("Current system does not support transparent windows, rendering dev tools with no transparency")
    }
}

@Composable
fun DtAnimatedWindow(
    windowId: WindowId? = null,
    windowState: WindowState = WindowState(),
    onCloseRequest: () -> Unit = {
        ShutdownRequest("Requested by user through $DEV_TOOLS").sendBlocking()
        exitProcess(0)
    },
    size: DpSize,
    visible: Boolean = true,
    title: String = "Untitled",
    icon: Painter? = null,
    undecorated: Boolean = true,
    transparent: Boolean = devToolsUseTransparency,
    resizable: Boolean = false,
    enabled: Boolean = true,
    focusable: Boolean = true,
    alwaysOnTop: Boolean = false,
    onPreviewKeyEvent: ((KeyEvent) -> Boolean) = { false },
    onKeyEvent: ((KeyEvent) -> Boolean) = { false },
    content: @Composable DialogWindowScope.() -> Unit,
) {
    // If you set `alwaysOnTop = true` for an invisible window in Linux, the window will lose its
    // ability to be always on top (even when it becomes visible again)
    val alwaysOnTopValue = alwaysOnTop && if (Os.currentOrNull() == Os.Linux) visible else true
    val initialPosition = calculateSideCarWindowPosition(windowState.position, size.width)

    DialogWindow(
        onCloseRequest = onCloseRequest,
        visible = visible,
        title = title,
        icon = icon,
        state = rememberDialogState(position = initialPosition, size = size),
        undecorated = undecorated,
        transparent = transparent,
        resizable = resizable,
        enabled = enabled,
        focusable = focusable,
        alwaysOnTop = alwaysOnTopValue,
        onPreviewKeyEvent = onPreviewKeyEvent,
        onKeyEvent = onKeyEvent,
    ) {
        window.tryForceToFront()

        val newPosition = animateWindowPosition(windowState.position, size)
        if (window.location != newPosition.toPoint()) {
            window.location = newPosition.toPoint()
        }

        LaunchedEffect(title, visible) {
            orchestration.messages.withType<ApplicationWindowGainedFocus>().collect { event ->
                if (event.windowId == windowId && visible) {
                    logger.debug("$windowId: $title 'toFront()'")
                    if (!window.tryForceToFront()) {
                        logger.debug("$windowId: $title 'toFront()' failed")
                    }
                }
            }
        }

        content()
    }
}

@Composable
internal fun animateWindowPosition(
    mainWindowPosition: WindowPosition,
    windowSize: DpSize,
): WindowPosition {
    val currentWidth = remember { mutableStateOf(windowSize.width) }
    val targetPosition = calculateSideCarWindowPosition(mainWindowPosition, windowSize.width)
    return when {
        currentWidth.value != windowSize.width -> {
            currentWidth.value = windowSize.width
            targetPosition
        }
        else -> {
            val x by animateDpAsState(targetPosition.x, animationSpec = tween(128))
            val y by animateDpAsState(targetPosition.y, animationSpec = tween(128))
            WindowPosition(x, y)
        }
    }
}

internal val sidecarWindowSize: DpSize = DpSize(
    width = DtSizes.largeLogoSize + DtPadding.small * 2,
    height = DtPadding.tinyElementPadding * 10              // number of elements (8) + 2 (top and bottom)
            + DtPadding.small * 2                           // top and bottom border padding
            + DtSizes.largeLogoSize + DtPadding.small * 2   // hot reload logo
            + DtSizes.iconSize * 6                          // action buttons
            + DtSizes.reloadCounterSize                     // reload counter
)

internal fun WindowPosition.toPoint(): Point = Point(x.value.toInt(), y.value.toInt())

internal fun calculateSideCarWindowPosition(mainWindowPosition: WindowPosition, width: Dp): WindowPosition {
    val targetX = mainWindowPosition.x - width - DtPadding.small * 3
    val targetY = mainWindowPosition.y
    return WindowPosition(targetX, targetY)
}

internal operator fun Point.plus(other: Point): Point = Point(x + other.x, y + other.y)

internal operator fun WindowPosition.plus(other: WindowPosition): WindowPosition =
    WindowPosition(x + other.x, y + other.y)

private fun transparencySupported(): Boolean = Try {
    val ge = GraphicsEnvironment.getLocalGraphicsEnvironment()
    return !ge.isHeadlessInstance && ge.screenDevices.all {
        it.isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSPARENT)
    }
}.leftOr { false }

internal fun ComposeDialog.tryForceToFront() = Try {
    val oldIsAlwaysOnTop = isAlwaysOnTop
    isAlwaysOnTop = true
    toFront()
    isAlwaysOnTop = oldIsAlwaysOnTop
    true
}.getOrNull() ?: false
