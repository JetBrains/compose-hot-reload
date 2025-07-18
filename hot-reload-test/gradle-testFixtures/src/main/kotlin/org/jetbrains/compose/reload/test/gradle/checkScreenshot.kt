/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.test.gradle

import org.jetbrains.compose.reload.core.asFileName
import org.jetbrains.compose.reload.core.withAsyncTrace
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.Screenshot
import org.jetbrains.compose.reload.test.core.TestEnvironment
import java.awt.Color
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import kotlin.io.path.createParentDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes
import kotlin.math.absoluteValue
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.test.fail

public suspend fun HotReloadTestFixture.checkScreenshot(name: String): Unit =
    withAsyncTrace("'checkScreenshot($name)'") run@{
        val screenshot = sendMessage(OrchestrationMessage.TakeScreenshotRequest()) {
            skipToMessage<Screenshot>()
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

        val expectedImage = expectFile.readBytes().inputStream().use { ImageIO.read(it) }
        val actualImage = screenshot.data.inputStream().use { ImageIO.read(it) }
        val ssimDiff = describeImageDifferences(expectedImage, actualImage)
        if (ssimDiff.isNotEmpty()) {
            val actualFile = expectFile.resolveSibling("${expectFile.nameWithoutExtension}-actual.${screenshot.format}")
            actualFile.writeBytes(screenshot.data)
            fail("Screenshot ${expectFile.toUri()} does not match\n" + ssimDiff.joinToString("\n"))
        }
    }


/**
 * @param expectedImage The binary representation of the expected image
 * @param actualImage The binary representation of the actual image
 * @param maxDiffValue The threshold of 'diff' value from which the images are to be considered 'non-equal': The diff
 * value is a number between 0 and 1, describing how different the images are. 0 means that the images are absolutely
 * identical. 1.0 would mean the complete opposite (every black pixel would be white and every white pixel would be black)
 * @param blur Window size for averaging nearby pixel values (this shall make the image diff more robust for
 * differences in, for example, antialiasing)
 *
 * @return The differences between the images in human-readable form, or an empty list if the images are
 * equal (enough)
 */
internal fun describeImageDifferences(
    expectedImage: BufferedImage,
    actualImage: BufferedImage,
    maxDifferenceValue: Double = 0.001,
): List<String> = buildList {
    if (expectedImage.width != actualImage.width) {
        add("Expected width '${expectedImage.width}', found '${actualImage.width}'")
    }

    if (expectedImage.height != actualImage.height) {
        add("Expected height '${expectedImage.height}', found '${actualImage.height}'")
    }

    val diff = ssimDiff(expectedImage, actualImage)
    if (diff > maxDifferenceValue) {
        add("Image difference value is ${diff.toString().take(4)}")
    }
}

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
    require(expectedImage.width == actualImage.width)
    require(expectedImage.height == actualImage.height)

    var diff = 0.0
    var countingPixels = 0
    for (xWindow in (0 until expectedImage.width).windowed(size = blur, 1, true)) {
        for (yWindow in (0 until expectedImage.height).windowed(size = blur, 1, true)) {
            val expectedColors = mutableListOf<Color>()
            val actualColors = mutableListOf<Color>()

            xWindow.forEach { x ->
                yWindow.forEach { y ->
                    expectedColors += Color(expectedImage.getRGB(x, y))
                    actualColors += Color(actualImage.getRGB(x, y))
                }
            }

            val expectedAverageColor = Color(
                expectedColors.averageBy { it.red }.roundToInt(),
                expectedColors.averageBy { it.green }.roundToInt(),
                expectedColors.averageBy { it.blue }.roundToInt()
            )

            val actualAverageColor = Color(
                actualColors.averageBy { it.red }.roundToInt(),
                actualColors.averageBy { it.green }.roundToInt(),
                actualColors.averageBy { it.blue }.roundToInt()
            )

            if (expectedAverageColor == backgroundColor && actualAverageColor == backgroundColor) {
                continue
            }

            countingPixels++

            /*
            Jeez, I should slow down a little:
            This is a very lazy implementation of a penalty score which will "downplay" small diffs as
            we know that most of our images will use black on white. Therefore, small diffs most likely
            are just some antialiasing artifacts.
             */
            fun penaltyScore(expected: Int, actual: Int): Double {
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

            diff += penaltyScore(expectedAverageColor.red, actualAverageColor.red)
            diff += penaltyScore(expectedAverageColor.green, actualAverageColor.green)
            diff += penaltyScore(expectedAverageColor.blue, actualAverageColor.blue)
        }
    }

    return diff / (countingPixels * 256 * 3).toFloat()
}

/**
 * Algorithm for measuring similarity between two images based on Structural similarity index measure
 * https://en.wikipedia.org/wiki/Structural_similarity_index_measure#Special-case_formula
 *
 * @param expectedImage The binary representation of the expected image
 * @param actualImage The binary representation of the actual image
 *
 * Expects that the expectedImage and the actualImage have the same size
 *
 * @return The difference between the images in range 0.0..1.0, 1.0 meaning two images are completely similar
 */
internal fun ssimDiff(
    expectedImage: BufferedImage,
    actualImage: BufferedImage,
): Double {
    require(expectedImage.width == actualImage.width)
    require(expectedImage.height == actualImage.height)

    /**
     * ssim value describes similarity between two images in range -1.0..1.0:
     * 1.0 indicates perfect similarity, 0.0 indicates no similarity, and -1.0 indicates perfect anti-correlation
     *
     * we normalise this value to fit into interval 0.0..1.0 and
     * then compute the difference value equal to (1.0 - normalisedSsim)
     */
    val ssim = ssim(expectedImage, actualImage)
    return 1.0 - (ssim + 1.0) / 2.0
}

// Constants used in SSIM computation
private const val L = 1.0
private const val K1 = 0.01
private const val K2 = 0.03

private const val C1 = (K1 * L) * (K1 * L)
private const val C2 = (K2 * L) * (K2 * L)

private fun ssim(
    expectedImage: BufferedImage, actualImage: BufferedImage,
): Double {
    val expected = expectedImage.toMatrix()
    val actual = actualImage.toMatrix()

    val meanExpected = expected.gaussianBlur()
    val meanActual = actual.gaussianBlur()

    val meanExpectedSqr = meanExpected.pow(2.0)
    val meanActualSqr = meanActual.pow(2.0)

    val varianceExpected = expected.pow(2.0).gaussianBlur() - meanExpectedSqr
    val varianceActual = actual.pow(2.0).gaussianBlur() - meanActualSqr
    val covariance = (expected * actual).gaussianBlur() - meanExpected * meanActual

    val ssimMatrix = ((2.0 * meanExpected * meanActual + C1) * (2.0 * covariance + C2)) /
        ((meanExpectedSqr + meanActualSqr + C1) * (varianceExpected + varianceActual + C2))
    return ssimMatrix.average()
}

private inline fun Matrix(width: Int, height: Int, initializer: (Int, Int) -> Double): Matrix {
    val matrix = Matrix(width, height)
    matrix.forEach { x, y ->
        matrix[x, y] = initializer(x, y)
    }
    return matrix
}

private class Matrix(
    val width: Int,
    val height: Int,
) {
    private val data: DoubleArray = DoubleArray(width * height)

    operator fun get(x: Int, y: Int): Double = data[x + y * width]

    operator fun set(x: Int, y: Int, value: Double) {
        data[x + y * width] = value
    }

    inline fun forEach(selector: (Int, Int) -> Unit) {
        for (y in 0 until height) {
            for (x in 0 until width) {
                selector(x, y)
            }
        }
    }

    operator fun plus(other: Matrix): Matrix = mapWithIndices { x, y, value -> value + other[x, y] }
    operator fun plus(scalar: Double): Matrix = map { it + scalar }
    operator fun minus(other: Matrix): Matrix = mapWithIndices { x, y, value -> value - other[x, y] }
    operator fun minus(scalar: Double): Matrix = map { it - scalar }
    operator fun times(other: Matrix): Matrix = mapWithIndices { x, y, value -> value * other[x, y] }
    operator fun times(scalar: Double): Matrix = map { it * scalar }
    operator fun div(other: Matrix): Matrix = mapWithIndices { x, y, value -> value / other[x, y] }
    operator fun div(scalar: Double): Matrix = map { it / scalar }
    fun pow(p: Double): Matrix = map { value -> value.pow(p) }

    fun min(): Double = data.min()
    fun max(): Double = data.max()
    fun sum(): Double = data.sum()
    fun average(): Double = data.average()
    inline fun sumOf(selector: (Double) -> Double): Double = data.sumOf(selector)

    inline fun sumByWithIndices(selector: (Int, Int, Double) -> Double): Double {
        var sum = 0.0
        forEach { x, y ->
            sum += selector(x, y, this[x, y])
        }
        return sum
    }

    inline fun map(mapper: (Double) -> Double): Matrix = Matrix(width, height) { x, y ->
        mapper(this[x, y])
    }

    inline fun mapWithIndices(mapper: (Int, Int, Double) -> Double): Matrix = Matrix(width, height) { x, y ->
        mapper(x, y, this[x, y])
    }

    inline fun applyFilter(filter: Matrix): Matrix =
        Matrix(
            width = width - filter.width + 1,
            height = height - filter.height + 1
        ) { x, y ->
            filter.sumByWithIndices { kx, ky, value ->
                value * this[x + ky, y + kx]
            }
        }

    fun gaussianBlur(): Matrix = applyFilter(GAUSSIAN_BLUR)

    companion object {
        /**
         * recommended filter size is 11x11 or 8x8
         * we use 8x8, because it is more sensitive to differences and also a bit more effective
         */
        private val GAUSSIAN_BLUR = gaussian(8, 1.5)

        private fun gaussian(size: Int, sigma: Double): Matrix {
            val filter = Matrix(size, size) { x, y, ->
                val dx = (x - size / 2).toDouble()
                val dy = (y - size / 2).toDouble()
                exp(-(dx * dx + dy * dy) / (2 * sigma * sigma))
            }
            return (1.0 * filter.sum()) * filter
        }
    }
}

private operator fun Double.plus(matrix: Matrix): Matrix = matrix + this
private operator fun Double.times(matrix: Matrix): Matrix = matrix * this

private fun BufferedImage.toMatrix(): Matrix =
    Matrix(width, height) { x, y -> this[x, y].toGreyScale() }

private operator fun BufferedImage.get(x: Int, y: Int) = Color(getRGB(x, y))

// https://en.wikipedia.org/wiki/Grayscale#Converting_color_to_grayscale
private fun Color.toGreyScale(): Double {
    val cLinear = 0.2126 * red / 255.0 + 0.7152 * green / 255.0 + 0.0722 * blue / 255.0
    return when {
        cLinear <= 0.0031308 -> 12.92 * cLinear
        else -> 1.055 * cLinear.pow(1 / 2.4) - 0.55
    }
}

private fun <T> Collection<T>.averageBy(selector: (T) -> Int): Double {
    var sum = 0.0
    var count = 0
    for (element in this) {
        sum += selector(element)
        ++count
    }
    return if (count == 0) Double.NaN else sum / count
}
