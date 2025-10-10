package org.jetbrains.compose.devtools.sidecar

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.awt.ComposeDialog
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.DialogWindowScope
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowScope
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.rememberDialogState
import kotlinx.coroutines.delay
import org.jetbrains.compose.devtools.invokeWhenMessageReceived
import org.jetbrains.compose.devtools.orchestration
import org.jetbrains.compose.devtools.sendBlocking
import org.jetbrains.compose.devtools.theme.DtPadding
import org.jetbrains.compose.devtools.theme.DtSizes
import org.jetbrains.compose.devtools.theme.DtTitles.DEV_TOOLS
import org.jetbrains.compose.reload.core.HotReloadEnvironment.devToolsAnimationsEnabled
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
import java.awt.Dimension
import java.awt.GraphicsDevice
import java.awt.GraphicsEnvironment
import java.awt.Point
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.milliseconds

private val logger = createLogger()

// animation time of window effects
internal val animationDuration =
    if (devToolsAnimationsEnabled) 512.milliseconds else 10.milliseconds

// check if transparency and opacity are supported
private val transparencySupported = transparencySupported()
internal val opacitySupported = devToolsTransparencyEnabled && transparencySupported

internal val devToolsUseTransparency = (devToolsTransparencyEnabled && transparencySupported).also {
    if (devToolsTransparencyEnabled && !transparencySupported) {
        logger.warn("Current system does not support transparent windows, rendering dev tools with no transparency")
    }
}

/**
 * Either windowState or initialSize and initialPosition should be specified
 */
@Composable
fun DtAnimatedWindow(
    windowId: WindowId? = null,
    windowState: WindowState = WindowState(),
    onStateUpdate: @Composable WindowScope.(skipAnimation: Boolean) -> Pair<DpSize, WindowPosition>,
    onCloseRequest: () -> Unit = {
        ShutdownRequest("Requested by user through $DEV_TOOLS").sendBlocking()
        exitProcess(0)
    },
    isExpandedByDefault: Boolean,
    initialSize: DpSize = getSideCarWindowSize(windowState.size, isExpandedByDefault),
    initialPosition: WindowPosition = getSideCarWindowPosition(windowState.position, initialSize.width),
    visible: Boolean = true,
    visibilityChanged: Boolean = false,
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

    DialogWindow(
        onCloseRequest = onCloseRequest,
        // if opacity is not supported --- make the window not visible
        // if opacity is supported --- control the visibility through opacity
        // this way we can be sure that the window will be animated properly
        visible = if (opacitySupported) true else visible,
        title = title,
        icon = icon,
        state = rememberDialogState(position = initialPosition, size = initialSize),
        undecorated = undecorated,
        transparent = transparent,
        resizable = resizable,
        enabled = enabled,
        focusable = focusable,
        alwaysOnTop = alwaysOnTopValue,
        onPreviewKeyEvent = onPreviewKeyEvent,
        onKeyEvent = onKeyEvent,
    ) {
        // if opacity is not supported, we hide the window by setting `visibility = false`
        // while the window is not visible, it does not render, so all the animations are saved
        // when the window changes its visibility --- all the animations are triggered
        // we need to skip them and render the window in its correct location straight away
        val skipAnimation = !opacitySupported && visibilityChanged
        val (newSize, newPosition) = onStateUpdate(this, skipAnimation)

        if (opacitySupported) {
            if (!visible) {
                window.opacity = 0.0f
            } else {
                window.opacity = 1.0f
            }
        }
        if (visibilityChanged && visible) {
            window.tryForceToFront()
        }

        if (window.size != newSize.toDimension()) {
            window.size = newSize.toDimension()
        }
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
internal fun animateWindowSize(
    mainWindowSize: DpSize,
    isExpanded: Boolean,
): DpSize {
    var currentIsExpanded by remember { mutableStateOf(isExpanded) }
    var currentSize by remember { mutableStateOf(getSideCarWindowSize(mainWindowSize, isExpanded)) }
    val targetSize = getSideCarWindowSize(mainWindowSize, isExpanded)
    /* No delay when we do not have the transparency enabled */
    if (!devToolsUseTransparency) {
        currentSize = targetSize
    }

    // We're closing
    if (currentIsExpanded && !isExpanded) {
        LaunchedEffect(Unit) {
            delay(animationDuration)
            currentIsExpanded = false
            currentSize = targetSize
        }
    }

    // We're opening
    if (!currentIsExpanded && isExpanded) {
        currentIsExpanded = true
        currentSize = targetSize
    }

    if (currentSize.height != targetSize.height) {
        currentSize = currentSize.copy(height = targetSize.height)
    }
    return currentSize
}

@Composable
internal fun animateWindowPosition(
    mainWindowPosition: WindowPosition,
    windowSize: DpSize,
    skipAnimation: Boolean,
): WindowPosition {
    val currentWidth = remember { mutableStateOf(windowSize.width) }
    val targetPosition = getSideCarWindowPosition(mainWindowPosition, windowSize.width)
    return when {
        currentWidth.value != windowSize.width -> {
            currentWidth.value = windowSize.width
            targetPosition
        }
        skipAnimation -> targetPosition
        else -> {
            val x by animateDpAsState(targetPosition.x, animationSpec = tween(128))
            val y by animateDpAsState(targetPosition.y, animationSpec = tween(128))
            WindowPosition(x, y)
        }
    }
}

internal fun DpSize.toDimension(): Dimension = Dimension(width.value.toInt(), height.value.toInt())
internal fun WindowPosition.toPoint(): Point = Point(x.value.toInt(), y.value.toInt())

internal fun getSideCarWindowPosition(mainWindowPosition: WindowPosition, width: Dp): WindowPosition {
    val targetX = mainWindowPosition.x - width - DtPadding.small * 3
    val targetY = mainWindowPosition.y
    return WindowPosition(targetX, targetY)
}

internal fun getSideCarWindowSize(mainWindowSize: DpSize, isExpanded: Boolean): DpSize {
    return when {
        isExpanded -> DpSize(DtSizes.sidecarWidth, maxOf(mainWindowSize.height, DtSizes.minSidecarHeight))
        else -> DpSize(
            width = DtSizes.largeLogoSize + DtPadding.small * 2,
            height = DtSizes.largeLogoSize + DtPadding.small * 2 + DtSizes.reloadCounterSize,
        )
    }
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

private fun ComposeDialog.tryForceToFront() = Try {
    val oldIsAlwaysOnTop = isAlwaysOnTop
    isAlwaysOnTop = true
    toFront()
    isAlwaysOnTop = oldIsAlwaysOnTop
    true
}.getOrNull() ?: false
