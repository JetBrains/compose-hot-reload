/*
 * Copyright 2024-2026 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.jvm

import org.jetbrains.compose.reload.core.Right
import org.jetbrains.compose.reload.core.Try
import org.jetbrains.compose.reload.core.WindowId
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.debug
import org.jetbrains.compose.reload.core.getOrThrow
import org.jetbrains.compose.reload.core.ifFailureThen
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
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageTypeSpecifier
import javax.imageio.metadata.IIOMetadataNode
import kotlin.math.roundToInt


private val logger = createLogger()

internal fun handleScreenshotRequest(request: ScreenshotRequest, window: Window, windowId: WindowId?): ScreenshotResult {
    logger.info("Taking screenshot: '${request.messageId}'")

    val screenshot = captureWindow(window)
    if (screenshot.isFailure()) {
        val errorMessage = screenshot.value.message ?: "Unknown error"
        logger.warn("Failed to capture window for screenshot request '${request.messageId}': $errorMessage")
        return ScreenshotResult(
            screenshotRequestId = request.messageId,
            isSuccess = false,
            errorMessage = errorMessage,
            windowId = windowId,
        )
    }

    try {
        val pngData = screenshot.getOrThrow().encodeAsPng()
        logger.debug("Sent screenshot: '${request.messageId}'")
        return ScreenshotResult(
            screenshotRequestId = request.messageId,
            format = "png",
            data = pngData,
            windowId = windowId,
        )
    } catch (e: Exception) {
        return ScreenshotResult(
            screenshotRequestId = request.messageId,
            isSuccess = false,
            errorMessage = e.message ?: "Unknown error",
            windowId = windowId,
        )
    }
}

/**
 * Captures the window content.
 *
 * The window decorations (title bar, borders) are excluded, so only the Compose content is captured.
 */
private fun captureWindow(window: Window): Try<Screenshot> {
    return captureWindowCompose(window)
        .ifFailureThen {
            captureWindowViaRobot(window)
        }
}

/**
 * Captures the window content using the Compose `ComposeDesktopEntryPoint`.
 */
private fun captureWindowCompose(window: Window): Try<Screenshot> {
    return Try {
        // After upgrading to Compose 1.13 or later, use this code instead of reflection
        // (window as? ComposeDesktopEntryPoint)?.captureContentToImage() ?: error("API not available")

        val desktopEntryPointInterface = Class.forName("androidx.compose.ui.ComposeDesktopEntryPoint")
        if (!desktopEntryPointInterface.isInstance(window))
            return Right(UnsupportedOperationException("ComposeDesktopEntryPoint.captureContentToImage not available"))

        val captureContentToImageMethod = desktopEntryPointInterface.getDeclaredMethod("captureContentToImage")
        Screenshot(
            image = captureContentToImageMethod.invoke(window) as BufferedImage,
            scale = window.graphicsConfiguration.defaultTransform.scaleX
        )

    }
}

private data class Screenshot(
    val image: BufferedImage,
    val scale: Double
)

/**
 * Captures the window content using [Robot.createScreenCapture].
 *
 * The window decorations (title bar, borders) are excluded, so only the Compose content is captured.
 */
private fun captureWindowViaRobot(window: Window): Try<Screenshot> {
    return Try {
        val robot = Robot()
        val location = window.locationOnScreen
        val insets = window.insets
        val rect = Rectangle(
            location.x + insets.left,
            location.y + insets.top,
            window.width - insets.left - insets.right,
            window.height - insets.top - insets.bottom,
        )
        Screenshot(
            image = robot.createScreenCapture(rect),
            scale = 1.0  // Robot always takes a screenshot at AWT pixel size
        )
    }
}

/**
 * Encodes the given screenshot (of the given window) as a PNG byte array.
 */
private fun Screenshot.encodeAsPng(): ByteArray {
    // pHYs stores resolution as an integer pixel count per meter, plus a unit flag.
    val inchesPerMeter = 39.3701
    val pixelsPerMeter = (scale * 72.0 * inchesPerMeter).roundToInt().toString()

    val writer = ImageIO.getImageWritersByFormatName("png").next()
    val typeSpecifier = ImageTypeSpecifier.createFromBufferedImageType(image.type)
    val metadata = writer.getDefaultImageMetadata(typeSpecifier, writer.defaultWriteParam)

    val physNode = IIOMetadataNode("pHYs")
    physNode.setAttribute("pixelsPerUnitXAxis", pixelsPerMeter)
    physNode.setAttribute("pixelsPerUnitYAxis", pixelsPerMeter)
    physNode.setAttribute("unitSpecifier", "meter")

    val root = IIOMetadataNode("javax_imageio_png_1.0")
    root.appendChild(physNode)
    metadata.mergeTree("javax_imageio_png_1.0", root)

    try {
        val buffer = ByteArrayOutputStream()
        ImageIO.createImageOutputStream(buffer).use { stream ->
            writer.setOutput(stream)
            writer.write(metadata, IIOImage(image, null, metadata), writer.defaultWriteParam)
        }
        return buffer.toByteArray()
    } finally {
        writer.dispose()
    }
}
