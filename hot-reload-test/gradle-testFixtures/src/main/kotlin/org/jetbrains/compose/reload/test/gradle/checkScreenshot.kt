/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.test.gradle

import org.jetbrains.compose.reload.core.asFileName
import org.jetbrains.compose.reload.core.testFixtures.describeImageDifferences
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
        val diff = describeImageDifferences(expectedImage, actualImage)
        if (diff.isNotEmpty()) {
            val actualFile = expectFile.resolveSibling("${expectFile.nameWithoutExtension}-actual.${screenshot.format}")
            actualFile.writeBytes(screenshot.data)
            fail("Screenshot ${expectFile.toUri()} does not match\n" + diff.joinToString("\n"))
        }
    }
