/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.core.testFixtures

import org.jetbrains.skiko.toImage
import java.awt.Color
import java.awt.image.BufferedImage
import kotlin.math.absoluteValue


/**
 * @param expectedImage The binary representation of the expected image
 * @param actualImage The binary representation of the actual image
 * @param maxDifferenceValue The threshold of 'diff' value from which the images are to be considered 'non-equal': The diff
 * value is a number between 0 and 1, describing how different the images are. 0 means that the images are absolutely
 * identical. 1.0 would mean the complete opposite (every black pixel would be white and every white pixel would be black)
 *
 * @return The differences between the images in human-readable form, or an empty list if the images are
 * equal (enough)
 */
fun describeImageDifferences(
    expectedImage: BufferedImage,
    actualImage: BufferedImage,
    maxDifferenceValue: Double = PIXEL_DIFF_SIMILARITY_THRESHOLD,
): List<String> = buildList {
    if (expectedImage.width != actualImage.width) {
        add("Expected width '${expectedImage.width}', found '${actualImage.width}'")
    }

    if (expectedImage.height != actualImage.height) {
        add("Expected height '${expectedImage.height}', found '${actualImage.height}'")
    }

    if (isNotEmpty()) return@buildList

    val diff = averagePixelValueDiff(expectedImage, actualImage)
    if (diff > 0) {
        println("Image difference value is ${diff.toString().take(5)}")
    }
    if (diff > maxDifferenceValue) {
        add("Image difference value is ${diff.toString().take(5)}")
    }
}

internal const val PIXEL_DIFF_SIMILARITY_THRESHOLD = 0.05

/**
 * @param expectedImage The binary representation of the expected image
 * @param actualImage The binary representation of the actual image
 * @param backgroundColor Pixels with background color will not be incorporated in calculating the 'diff value'
 * @param blur Window size for averaging nearby pixel values (this shall make the image diff more robust for
 * differences in, for example, antialiasing)
 *
 * Expects that the expectedImage and the actualImage have the same size
 *
 * @return The difference between the images in range 0.0..1.0, 1.0 meaning two images are completely similar
 */
internal fun averagePixelValueDiff(
    expectedImage: BufferedImage, actualImage: BufferedImage,
    backgroundColor: Color = Color.WHITE,
    blur: Int = 3
): Double {
    if (true) return imageDiff(expectedImage.toImage(), actualImage.toImage()).value.toDouble()

    require(expectedImage.width == actualImage.width)
    require(expectedImage.height == actualImage.height)
    val boundingBox = getBoundingBox(expectedImage, actualImage, backgroundColor)
        .extendBy(pixels = blur / 2, maxWidth = expectedImage.width, maxHeight = expectedImage.height)

    val blurBox = blurBox(blur / 2)

    var diff = 0.0
    var countingPixels = 0
    for (x in boundingBox.xRange) {
        for (y in boundingBox.yRange) {
            var expectedRed = 0
            var expectedGreen = 0
            var expectedBlue = 0
            var actualRed = 0
            var actualGreen = 0
            var actualBlue = 0
            var count = 0

            for ((dx, dy) in blurBox) {
                if (x + dx < 0 || x + dx >= expectedImage.width || y + dy < 0 || y + dy >= expectedImage.height) {
                    continue
                }
                val expectedRgb = expectedImage.getRGB(x + dx, y + dy)
                val actualRgb = actualImage.getRGB(x + dx, y + dy)

                expectedRed += (expectedRgb shr 16) and 0xFF
                expectedGreen += (expectedRgb shr 8) and 0xFF
                expectedBlue += expectedRgb and 0xFF

                actualRed += (actualRgb shr 16) and 0xFF
                actualGreen += (actualRgb shr 8) and 0xFF
                actualBlue += actualRgb and 0xFF

                count++
            }

            val expectedAverageColor = Color(expectedRed / count, expectedGreen / count, expectedBlue / count)
            val actualAverageColor = Color(actualRed / count, actualGreen / count, actualBlue / count)

            if (expectedAverageColor == backgroundColor && actualAverageColor == backgroundColor) {
                continue
            }

            countingPixels++

            diff += penaltyScore(expectedAverageColor.red, actualAverageColor.red)
            diff += penaltyScore(expectedAverageColor.green, actualAverageColor.green)
            diff += penaltyScore(expectedAverageColor.blue, actualAverageColor.blue)
        }
    }

    return diff / (countingPixels * 256 * 3).toFloat()
}

/*
This is a very lazy implementation of a penalty score which will "downplay" small diffs as
we know that most of our images will use black on white. Therefore, small diffs most likely
are just some antialiasing artifacts.
 */
private fun penaltyScore(expected: Int, actual: Int): Double {
    val raw = (expected - actual).absoluteValue
    return when {
        raw < 10 -> 0.0
        raw < 20 -> 0.5
        raw < 100 -> 1.0
        raw < 150 -> 3.0
        raw < 200 -> 20.0
        else -> raw.toDouble()
    }
}

private data class BoundingBox(val x: Int, val y: Int, val width: Int, val height: Int) {
    val xRange: IntRange get() = x until (x + width)
    val yRange: IntRange get() = y until (y + height)

    fun extendBy(pixels: Int, maxHeight: Int, maxWidth: Int): BoundingBox = BoundingBox(
        x = maxOf(x - pixels, 0),
        y = maxOf(y - pixels, 0),
        width = minOf(width + (pixels * 2), maxWidth),
        height = minOf(height + (pixels * 2), maxHeight),
    )
}

private val r1Box = listOf(-1 to -1, -1 to 0, -1 to 1, 0 to -1, 0 to 1, 1 to -1, 1 to 0, 1 to 1)
private fun blurBox(r: Int): List<Pair<Int, Int>> = when (r) {
    1 -> r1Box
    else -> buildList {
        for (x in -r..r) {
            for (y in -r..r) {
                add(x to y)
            }
        }
    }
}

private fun getBoundingBox(
    expectedImage: BufferedImage,
    actualImage: BufferedImage,
    backgroundColor: Color,
): BoundingBox {
    val backgroundRgb = backgroundColor.rgb
    var minX = expectedImage.width
    var minY = expectedImage.height
    var maxX = 0
    var maxY = 0

    for (y in 0 until expectedImage.height) {
        // left
        for (x in 0 until expectedImage.width) {
            if (expectedImage.getRGB(x, y) != backgroundRgb || actualImage.getRGB(x, y) != backgroundRgb) {
                minX = minOf(minX, x)
                minY = minOf(minY, y)
                break
            }
        }

        // right
        for (x in expectedImage.width - 1 downTo 0) {
            if (expectedImage.getRGB(x, y) != backgroundRgb || actualImage.getRGB(x, y) != backgroundRgb) {
                maxX = maxOf(maxX, x)
                minY = minOf(minY, y)
                break
            }
        }
    }

    for (x in maxOf(0, minX - 1)..minOf(expectedImage.width - 1, maxX + 1)) {
        // top
        for (y in 0 until expectedImage.height) {
            if (expectedImage.getRGB(x, y) != backgroundRgb || actualImage.getRGB(x, y) != backgroundRgb) {
                minX = minOf(minX, x)
                minY = minOf(minY, y)
                break
            }
        }

        // bottom
        for (y in expectedImage.height - 1 downTo 0) {
            if (expectedImage.getRGB(x, y) != backgroundRgb || actualImage.getRGB(x, y) != backgroundRgb) {
                maxX = maxOf(maxX, x)
                maxY = maxOf(maxY, y)
                break
            }
        }
    }

    return when {
        minX > maxX || minY > maxY -> BoundingBox(0, 0, expectedImage.width, expectedImage.height)
        else -> BoundingBox(minX, minY, maxX - minX + 1, maxY - minY + 1)
    }
}
