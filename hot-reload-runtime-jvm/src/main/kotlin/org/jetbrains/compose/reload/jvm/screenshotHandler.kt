/*
 * Copyright 2024-2026 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.jvm

import org.jetbrains.compose.reload.core.Try
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

internal fun handleScreenshotRequest(request: ScreenshotRequest, window: Window): ScreenshotResult {
    logger.info("Taking screenshot: '${request.messageId}'")

    val capture = captureWindow(window)
    if (capture.isFailure()) {
        val errorMessage = capture.value.message ?: "Unknown error"
        logger.warn("Failed to capture window for screenshot request '${request.messageId}': $errorMessage")
        return ScreenshotResult(
            screenshotRequestId = request.messageId,
            isSuccess = false,
            errorMessage = errorMessage
        )
    }

    val baos = ByteArrayOutputStream()
    ImageIO.write(capture.getOrThrow(), "png", baos)
    logger.debug("Sent screenshot: '${request.messageId}'")
    return ScreenshotResult(
        screenshotRequestId = request.messageId,
        format = "png",
        data = baos.toByteArray()
    )
}

/**
 * Captures the window content using [Robot.createScreenCapture].
 *
 * It captures the window title in addition to Compose content,
 * so AI agent can analyze and understand the full context of the window.
 */
internal fun captureWindow(window: Window): Try<BufferedImage> {
    return Try {
        val robot = Robot()
        val location = window.locationOnScreen
        val rect = Rectangle(location.x, location.y, window.width, window.height)
        robot.createScreenCapture(rect)
    }
}
