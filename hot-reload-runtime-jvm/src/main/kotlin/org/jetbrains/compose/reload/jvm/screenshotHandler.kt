/*
 * Copyright 2024-2026 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.jvm

import org.jetbrains.compose.reload.core.Os
import org.jetbrains.compose.reload.core.Try
import org.jetbrains.compose.reload.core.WindowId
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.debug
import org.jetbrains.compose.reload.core.getOrThrow
import org.jetbrains.compose.reload.core.info
import org.jetbrains.compose.reload.core.isFailure
import org.jetbrains.compose.reload.core.warn
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ScreenshotRequest
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ScreenshotResult
import java.awt.Rectangle
import java.awt.Robot
import java.awt.Window
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

private val logger = createLogger()

internal fun handleScreenshotRequest(request: ScreenshotRequest, window: Window, windowId: WindowId?): ScreenshotResult {
    logger.info("Taking screenshot: '${request.messageId}'")

    val capture = captureWindow(window)
    if (capture.isFailure()) {
        val errorMessage = capture.value.let {
            "${it.javaClass.simpleName}: ${it.message ?: "Unknown error"}"
        }
        logger.warn("Failed to capture window for screenshot request '${request.messageId}': $errorMessage")
        return ScreenshotResult(
            screenshotRequestId = request.messageId,
            isSuccess = false,
            errorMessage = errorMessage,
            windowId = windowId,
        )
    }

    val baos = ByteArrayOutputStream()
    ImageIO.write(capture.getOrThrow(), "png", baos)
    logger.debug("Sent screenshot: '${request.messageId}'")
    return ScreenshotResult(
        screenshotRequestId = request.messageId,
        format = "png",
        data = baos.toByteArray(),
        windowId = windowId,
    )
}

/**
 * Captures the window content, including the window title in addition to the Compose content,
 * so an AI agent can analyze and understand the full context of the window.
 *
 * Where available, a platform-specific native implementation is used. Unlike the [Robot] based
 * capture (which reads pixels off the screen and therefore includes anything covering the window),
 * the native implementations render the window's own backing store and work even when the window is
 * obscured by other windows. If the native capture is unavailable or fails, we fall back to [Robot].
 */
internal fun captureWindow(window: Window): Try<BufferedImage> {
    return Try {
        captureWindowNative(window) ?: captureWindowWithRobot(window)
    }
}

/**
 * Platform-specific native capture that works even when the window is obscured. Returns `null`
 * (after logging a reason) when no native implementation is available for the current OS or the
 * native capture fails, so the caller can fall back to [captureWindowWithRobot].
 */
private fun captureWindowNative(window: Window): BufferedImage? {
    return when (Os.currentOrNull()) {
        Os.MacOs -> captureWindowMacOs(window)
        Os.Windows -> captureWindowWindows(window)
        Os.Linux -> captureWindowLinux(window)
        else -> null
    }
}

/**
 * Captures the window content using [Robot.createScreenCapture]. Note that this reads pixels off the
 * actual screen, so anything in front of the window will appear in the screenshot.
 */
private fun captureWindowWithRobot(window: Window): BufferedImage {
    val robot = Robot()
    val location = window.locationOnScreen
    val rect = Rectangle(location.x, location.y, window.width, window.height)
    return robot.createScreenCapture(rect)
}
