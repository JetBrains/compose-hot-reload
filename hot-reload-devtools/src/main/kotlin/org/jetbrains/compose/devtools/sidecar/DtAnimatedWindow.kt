package org.jetbrains.compose.devtools.sidecar

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.DialogWindowScope
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowScope
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.rememberDialogState
import kotlinx.coroutines.delay
import org.jetbrains.compose.devtools.invokeWhenMessageReceived
import org.jetbrains.compose.devtools.sendBlocking
import org.jetbrains.compose.devtools.theme.DtTitles.DEV_TOOLS
import org.jetbrains.compose.reload.core.HotReloadEnvironment.devToolsTransparencyEnabled
import org.jetbrains.compose.reload.core.WindowId
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.debug
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ApplicationWindowGainedFocus
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ShutdownRequest
import java.awt.Dimension
import java.awt.Point
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.milliseconds

private val logger = createLogger()

// animation time of window effects
internal val animationDuration = 512.milliseconds

/**
 * Either windowState or initialSize and initialPosition should be specified
 */
@Composable
fun DtAnimatedWindow(
    windowId: WindowId? = null,
    windowState: WindowState = WindowState(),
    onStateUpdate: @Composable WindowScope.() -> Pair<DpSize, WindowPosition>,
    onCloseRequest: () -> Unit = {
        ShutdownRequest("Requested by user through $DEV_TOOLS").sendBlocking()
        exitProcess(0)
    },
    isExpandedByDefault: Boolean,
    initialSize: DpSize = getSideCarWindowSize(windowState.size, isExpandedByDefault),
    initialPosition: WindowPosition = getSideCarWindowPosition(windowState.position, initialSize.width),
    visible: Boolean = true,
    title: String = "Untitled",
    icon: Painter? = null,
    undecorated: Boolean = true,
    transparent: Boolean = devToolsTransparencyEnabled,
    resizable: Boolean = false,
    enabled: Boolean = true,
    focusable: Boolean = true,
    alwaysOnTop: Boolean = false,
    onPreviewKeyEvent: ((KeyEvent) -> Boolean) = { false },
    onKeyEvent: ((KeyEvent) -> Boolean) = { false },
    content: @Composable DialogWindowScope.() -> Unit,
) {
    DialogWindow(
        onCloseRequest = onCloseRequest,
        visible = visible,
        title = title,
        icon = icon,
        state = rememberDialogState(position = initialPosition, size = initialSize),
        undecorated = undecorated,
        transparent = transparent,
        resizable = resizable,
        enabled = enabled,
        focusable = focusable,
        alwaysOnTop = alwaysOnTop,
        onPreviewKeyEvent = onPreviewKeyEvent,
        onKeyEvent = onKeyEvent,
    ) {
        val (newSize, newPosition) = onStateUpdate(this)
        if (window.size != newSize.toDimension()) {
            window.size = newSize.toDimension()
        }
        if (window.location != newPosition.toPoint()) {
            window.location = newPosition.toPoint()
        }

        invokeWhenMessageReceived<ApplicationWindowGainedFocus> { event ->
            if (event.windowId == windowId) {
                logger.debug("$windowId: Sidecar window 'toFront()'")
                window.toFront()
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
    if (!devToolsTransparencyEnabled) {
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
): WindowPosition {
    val currentWidth = remember { mutableStateOf(windowSize.width) }
    val targetPosition = getSideCarWindowPosition(mainWindowPosition, windowSize.width)
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

internal fun DpSize.toDimension(): Dimension = Dimension(width.value.toInt(), height.value.toInt())
internal fun Dimension.toDpSize(): DpSize = DpSize(width.dp, height.dp)

internal fun Point.toWindowPosition(): WindowPosition = WindowPosition(x.dp, y.dp)
internal fun WindowPosition.toPoint(): Point = Point(x.value.toInt(), y.value.toInt())

internal fun getSideCarWindowPosition(mainWindowPosition: WindowPosition, width: Dp): WindowPosition {
    val targetX = mainWindowPosition.x - width - if (!devToolsTransparencyEnabled) 12.dp else 0.dp
    val targetY = mainWindowPosition.y
    return WindowPosition(targetX, targetY)
}

internal fun getSideCarWindowSize(mainWindowSize: DpSize, isExpanded: Boolean): DpSize {
    return DpSize(
        width = if (isExpanded) 512.dp else 32.dp + 4.dp + (12.dp.takeIf { devToolsTransparencyEnabled } ?: 0.dp),
        height = if (isExpanded) maxOf(mainWindowSize.height, 512.dp)
        else if (devToolsTransparencyEnabled) maxOf(mainWindowSize.height, 512.dp) else 28.dp + 4.dp,
    )
}

internal operator fun Point.plus(other: Point): Point = Point(x + other.x, y + other.y)

internal operator fun WindowPosition.plus(other: WindowPosition): WindowPosition =
    WindowPosition(x + other.x, y + other.y)

