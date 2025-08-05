/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.devtools.utils

import androidx.compose.ui.test.ExperimentalTestApi
import org.jetbrains.compose.reload.core.asFileName
import org.jetbrains.compose.reload.core.testFixtures.imageDiff
import org.jetbrains.compose.reload.core.testFixtures.readImage
import org.jetbrains.compose.reload.test.core.TestEnvironment
import org.junit.jupiter.api.fail
import kotlin.io.path.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.exists
import kotlin.io.path.nameWithoutExtension
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

    val expectImage = expectFile.readImage()

    if (expectImage.width != actualImage.width || expectImage.height != actualImage.height) fail {
        "Screenshot: $name has different size than expected. " +
            "Expected: ${expectImage.width}x${expectImage.height}, got: ${actualImage.width}x${actualImage.height}"
    }

    val diff = imageDiff(expectImage, actualImage)
    if (diff.isDifferent()) {
        val actualFile = expectFile.resolveSibling(expectFile.nameWithoutExtension + "-actual.png")
        val diffFile = expectFile.resolveSibling(expectFile.nameWithoutExtension + "-diff.png")
        actualFile.writeBytes(actualImage.encodeToData()!!.bytes)
        diffFile.writeBytes(diff.diffImage.encodeToData()!!.bytes)
        fail(
            "Actual and diff images are not equal (score=${diff.score})\n" +
                "Expected: ${expectFile.toUri()}\n" +
                "Generated: ${actualFile.toUri()}\n" +
                "Diff: ${diffFile.toUri()}"
        )
    }
}
