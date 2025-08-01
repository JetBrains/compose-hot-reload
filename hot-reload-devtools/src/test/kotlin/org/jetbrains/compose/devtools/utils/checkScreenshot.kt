/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.devtools.utils

import androidx.compose.ui.test.ExperimentalTestApi
import org.jetbrains.compose.reload.core.asFileName
import org.jetbrains.compose.reload.core.testFixtures.describeImageDifferences
import org.jetbrains.compose.reload.core.testFixtures.imageDiff
import org.jetbrains.compose.reload.test.core.TestEnvironment
import org.jetbrains.skia.Image
import org.jetbrains.skiko.toBitmap
import org.jetbrains.skiko.toBufferedImage
import org.jetbrains.skiko.toImage
import org.junit.jupiter.api.fail
import javax.imageio.ImageIO
import kotlin.io.path.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.exists
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes


interface ScreenshotTest

@OptIn(ExperimentalTestApi::class)
context(fixture: DtScreenshotTestFixture, testScope: DtScreenshotTestScope)
internal fun checkScreenshot(name: String) {
    val actualImage = testScope.takeScreenshot()

    val expectFile = Path("src/test/resources/screenshots")
        .resolve(fixture.context.testClassName.asFileName())
        .resolve(fixture.context.testMethodName.asFileName())
        .resolve("$name-${fixture.context.width}x${fixture.context.height}.png")

    if (!expectFile.exists() || TestEnvironment.updateTestData) {
        expectFile.createParentDirectories()
        expectFile.writeBytes(actualImage.encodeToData()!!.bytes)
        if (TestEnvironment.updateTestData) return
        fail("Expected screenshot file does not exist; Generated: ${expectFile.toUri()}")
    }

    val expectImage = ImageIO.read(expectFile.toFile()).toImage()

    if (expectImage.width != actualImage.width || expectImage.height != actualImage.height) fail {
        "Screenshot: $name has different size than expected. " +
            "Expected: ${expectImage.width}x${expectImage.height}, got: ${actualImage.width}x${actualImage.height}"
    }

    imageDiff(expectImage, actualImage)

    /*
    These, more complex, screenshots will be compared in 32x32 chunks.
     */
    /*val boxSize = 32
    val horizontalSplits = expectImage.width / boxSize
    val verticalSplits = expectImage.height / boxSize

    for (horizontalSplit in 0 until horizontalSplits) {
        for (verticalSplit in 0 until verticalSplits) {
            val x = boxSize * horizontalSplit
            val y = boxSize * verticalSplit
            val width = if (horizontalSplit == horizontalSplits - 1) expectImage.width - x else boxSize
            val height = if (verticalSplit == verticalSplits - 1) expectImage.height - y else boxSize

            val actualSubimage = actualImage.getSubimage(x, y, width, height)
            val expectSubimage = expectImage.getSubimage(x, y, width, height)
            val difference = describeImageDifferences(expectSubimage, actualSubimage)

            if (difference.isNotEmpty()) {
                val actualFile = expectFile.resolveSibling("${expectFile.nameWithoutExtension}-actual.png")
                ImageIO.write(actualImage, "png", actualFile.toFile())
                fail("Screenshot: $name does not match\nGenerated: ${actualFile.toUri()}")
            }
        }
    }*/
}
