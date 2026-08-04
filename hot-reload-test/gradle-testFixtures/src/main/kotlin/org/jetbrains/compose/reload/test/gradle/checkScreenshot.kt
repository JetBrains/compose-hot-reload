/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.test.gradle

import org.jetbrains.compose.reload.InternalHotReloadApi
import org.jetbrains.compose.reload.core.asFileName
import org.jetbrains.compose.reload.core.withAsyncTrace
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import org.jetbrains.compose.reload.test.core.TestEnvironment
import org.jetbrains.kotlin.tooling.core.Extras
import org.jetbrains.kotlin.tooling.core.extrasKeyOf
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Color
import org.jetbrains.skia.Image
import org.w3c.dom.Node
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.nio.file.Path
import javax.imageio.ImageIO
import javax.imageio.ImageReader
import kotlin.io.path.createParentDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes
import kotlin.math.roundToInt
import kotlin.test.fail


/**
 * Allows for configuring the [checkScreenshot] function withing a test.
 * This annotation can be used to target the entire test class or a individual test method.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
public annotation class CheckScreenshot(
    /**
     * See [compare] colorTolerance:
     * This value describes a 'search' tolerance in the color value (from 0 to 1.0)
     */
    val colorTolerance: Float = COMPARE_DEFAULT_COLOR_TOLERANCE,

    /**
     * See [compare] radius:
     * This value describes the 'search radius' for a given pixel in the expect image:
     * For each actual image pixel, we try to find the corresponding to expect pixel within this radius.
     */
    val radius: Int = COMPARE_DEFAULT_RADIUS,
) {
    @InternalHotReloadApi
    public companion object {
        public val key: Extras.Key<CheckScreenshot> = extrasKeyOf()
    }
}

public suspend fun HotReloadTestFixture.checkScreenshot(name: String): Unit =
    withAsyncTrace("'checkScreenshot($name)'") run@{
        val request = OrchestrationMessage.ScreenshotRequest()
        val screenshot = sendMessage(request) {
            skipToMessage<OrchestrationMessage.ScreenshotResult> {
                it.screenshotRequestId == request.messageId
            }
        }

        if (!screenshot.isSuccess) {
            fail("Screenshot failed: ${screenshot.errorMessage ?: "unknown error"}")
        }

        val directory = screenshotsDirectory()
            .resolve(testClassName.asFileName().replace(".", "/"))
            .resolve(testMethodName.asFileName())

        val screenshotName = "$name.${screenshot.format}"
        val expectFile = directory.resolve(screenshotName)

        if (TestEnvironment.updateTestData) {
            expectFile.deleteIfExists()
            expectFile.createParentDirectories()
            expectFile.writeBytes(screenshot.data)
            return@run
        }

        if (!expectFile.exists()) {
            expectFile.createParentDirectories()
            expectFile.writeBytes(screenshot.data)
            fail("Screenshot '${expectFile.toUri()}' did not exist; Generated")
        }

        val (expectImage, expectPpi) = expectFile.readJavaImage()
        val (actualImage, _) = screenshot.data.readJavaImage(targetPpi = expectPpi)
        val params = extras[CheckScreenshot.key] ?: CheckScreenshot()
        val diff = describeImageDifferences(params, expectImage, actualImage)
        if (diff.isNotEmpty()) {
            val actualFile = expectFile.resolveSibling("${expectFile.nameWithoutExtension}-actual.${screenshot.format}")
            actualFile.writeBytes(screenshot.data)
            fail("Screenshot ${expectFile.toUri()} does not match\n" + diff.joinToString("\n"))
        }
    }

/**
 * @param expectImage The binary representation of the expected image
 * @param actualImage The binary representation of the actual image
 */
internal fun describeImageDifferences(
    params: CheckScreenshot,
    expectImage: BufferedImage, actualImage: BufferedImage,
): List<String> = buildList {
    if (expectImage.width != actualImage.width) {
        add("Expected width '${expectImage.width}', found '${actualImage.width}'")
    }

    if (expectImage.height != actualImage.height) {
        add("Expected height '${expectImage.height}', found '${actualImage.height}'")
    }

    if (isNotEmpty()) return@buildList

    val badPixels = countBadPixels(expectImage.toSkikoImage(), actualImage.toSkikoImage(), params)
    if (badPixels > 0) add("Found '$badPixels' pixels which cannot be found in the 'expectImage'")
}

internal fun countBadPixels(
    expectImage: Image, actualImage: Image,
    params: CheckScreenshot = CheckScreenshot(),
): Int {
    val comparisonImage = compare(
        expect = expectImage,
        actual = actualImage,
        colorTolerance = params.colorTolerance, radius = params.radius
    )
    val comparisonBitmap = Bitmap.makeFromImage(comparisonImage)

    var badPixels = 0

    for (x in 0 until expectImage.width) {
        for (y in 0 until expectImage.height) {
            val color = comparisonBitmap.getColor(x, y)
            if (Color.getR(color) > 0 || Color.getG(color) > 0 || Color.getB(color) > 0) {
                badPixels++
            }
        }
    }

    return badPixels
}

private fun Path.readJavaImage(targetPpi: Double? = null) = readBytes().readJavaImage(targetPpi)

private fun ByteArray.readJavaImage(targetPpi: Double? = null): Pair<BufferedImage, Double?> {
    ImageIO.createImageInputStream(ByteArrayInputStream(this)).use { iis ->
        val reader = ImageIO.getImageReaders(iis).next()
        try {
            reader.setInput(iis)
            val image = reader.read(0)
            val ppi = reader.getPpi() ?: 72.0
            val scaledImage = if ((targetPpi != null) && (ppi != targetPpi)) image.scale(targetPpi / ppi) else image
            return Pair(scaledImage, targetPpi ?: ppi)
        } finally {
            reader.dispose()
        }
    }
}

private fun ImageReader.getPpi(): Double? {
    fun findChild(parent: Node, name: String): Node? {
        val children = parent.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (name == child.nodeName) {
                return child
            }
        }
        return null
    }

    val metadata = this.getImageMetadata(0)
    if (metadata == null || !metadata.isStandardMetadataFormatSupported) {
        return null
    }

    val dimension = findChild(metadata.getAsTree("javax_imageio_1.0"), "Dimension") ?: return null
    val horizontalPixelSize = findChild(dimension, "HorizontalPixelSize") ?: return null

    val mmPerPixel = horizontalPixelSize.attributes.getNamedItem("value").nodeValue.toDouble()
    return 25.4 / mmPerPixel
}

private fun BufferedImage.scale(factor: Double): BufferedImage {
    val scaledWidth = (width * factor).roundToInt().coerceAtLeast(1)
    val scaledHeight = (height * factor).roundToInt().coerceAtLeast(1)

    val scaled = BufferedImage(scaledWidth, scaledHeight, type)
    val g = scaled.createGraphics()
    try {
        g.setRenderingHint(
            RenderingHints.KEY_INTERPOLATION,
            RenderingHints.VALUE_INTERPOLATION_BICUBIC
        )
        g.setRenderingHint(
            RenderingHints.KEY_RENDERING,
            RenderingHints.VALUE_RENDER_QUALITY
        )
        g.setRenderingHint(
            RenderingHints.KEY_ANTIALIASING,
            RenderingHints.VALUE_ANTIALIAS_ON
        )
        g.drawImage(this, 0, 0, scaledWidth, scaledHeight, null)
    } finally {
        g.dispose()
    }
    return scaled
}
