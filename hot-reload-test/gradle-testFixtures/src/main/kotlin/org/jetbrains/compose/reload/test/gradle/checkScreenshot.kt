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
import javax.imageio.ImageIO
import kotlin.io.path.createParentDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes
import kotlin.math.absoluteValue
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

        val imageDiff = describeDifference(expectFile.readBytes(), screenshot.data)

        if (imageDiff.isNotEmpty()) {
            val actualFile = expectFile.resolveSibling("${expectFile.nameWithoutExtension}-actual.${screenshot.format}")
            actualFile.writeBytes(screenshot.data)
            fail("Screenshot ${expectFile.toUri()} does not match\n" + imageDiff.joinToString("\n"))
        }
    }


/**
 * @param expected The binary representation of the expected image
 * @param actual The binary representation of the actual image
 * @param backgroundColor Pixels with background color will not be incorporated in calculating the 'diff value'.
 * @param maxDiffValue The threshold of 'diff' value from which the images are to be considered 'non-equal': The diff
 * value is a number between 0 and 1, describing how different the images are. 0 means that the images are absolutely
 * identical. 1.0 would mean the complete opposite (every black pixel would be white and every white pixel would be black)
 * @param blur Window size for averaging nearby pixel values (this shall make the image diff more robust for
 * differences in, for example, antialiasing)
 *
 * @return The differences between the images in human-readable form, or an empty list if the images are
 * equal (enough)
 */
internal fun describeDifference(
    expected: ByteArray, actual: ByteArray,
    backgroundColor: Color = Color.WHITE,
    maxDiffValue: Float = 0.01f,
    blur: Int = 3
): List<String> {
    val expectedImage = expected.inputStream().use { ImageIO.read(it) }
    val actualImage = actual.inputStream().use { ImageIO.read(it) }
    val diffs = mutableListOf<String>()

    if (expectedImage.width != actualImage.width) {
        diffs.add("Expected width '${expectedImage.width}', found '${actualImage.width}'")
    }

    if (expectedImage.height != actualImage.height) {
        diffs.add("Expected height '${expectedImage.height}', found '${actualImage.height}'")
    }

    /* Return early if dimensions do not match */
    if (diffs.isNotEmpty()) return diffs

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

    val diffFraction = diff / (countingPixels * 256 * 3).toFloat()
    if (diffFraction > maxDiffValue) {
        diffs.add("Image diff value is ${diffFraction.toString().take(4)}")
    }

    return diffs
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
