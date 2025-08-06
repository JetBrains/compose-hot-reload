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
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Color
import org.jetbrains.skia.Image
import kotlin.io.path.createParentDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.writeBytes
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

        val expectImage = expectFile.readImage()
        val actualImage = screenshot.data.readImage()
        val diff = describeImageDifferences(expectImage, actualImage)
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
internal fun describeImageDifferences(expectImage: Image, actualImage: Image): List<String> = buildList {
    if (expectImage.width != actualImage.width) {
        add("Expected width '${expectImage.width}', found '${actualImage.width}'")
    }

    if (expectImage.height != actualImage.height) {
        add("Expected height '${expectImage.height}', found '${actualImage.height}'")
    }

    if (isNotEmpty()) return@buildList

    val badPixels = countBadPixels(expectImage, actualImage)
    if (badPixels > 0) add("Found '$badPixels' pixels which cannot be found in the 'expectImage'")
}

internal fun countBadPixels(expectImage: Image, actualImage: Image): Int {
    val comparisonImage = compare(expectImage, actualImage)
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
