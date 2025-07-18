/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.test.gradle

import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CheckScreenshotTest {
    companion object {
        private const val PIXEL_AVERAGE_THRESHOLD = 0.01
        private const val SSIM_THRESHOLD = 0.001
    }

    private fun forEachTestFolder(selector: (File) -> Unit) {
        File(this::class.java.classLoader.getResource("screenshots").file)
            .listFiles()
            .forEach {
                if (it.isDirectory) selector(it)
            }
    }

    @Test
    fun `test - averagePixelValueDiff`() {
        forEachTestFolder { testFolder ->
            val images = testFolder.listFiles().associate { file ->
                file.absolutePath to file.inputStream().use { reader -> ImageIO.read(reader) }
            }

            for (file1 in images.keys) {
                for (file2 in images.keys) {
                    val pixelDiff = averagePixelValueDiff(images[file1]!!, images[file2]!!)
                    if (file1 == file2) {
                        assertEquals(
                            0.0, pixelDiff,
                            """Screenshots '$file1' and '$file2' should be equal, but marked different with difference $pixelDiff"""
                        )
                    } else {
                        assertTrue(
                            pixelDiff > PIXEL_AVERAGE_THRESHOLD,
                            """Screenshots '$file1' and '$file2' should be different, but marked similar with difference $pixelDiff"""
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `test - ssimDiff`() {
        forEachTestFolder { testFolder ->
            val images = testFolder.listFiles().associate { file ->
                file.absolutePath to file.inputStream().use { reader -> ImageIO.read(reader) }
            }

            for (file1 in images.keys) {
                for (file2 in images.keys) {
                    val ssimDiff = ssimDiff(images[file1]!!, images[file2]!!)
                    if (file1 == file2) {
                        assertEquals(
                            0.0, ssimDiff,
                            """Screenshots '$file1' and '$file2' should be equal, but marked different with difference $ssimDiff"""
                        )
                    } else {
                        assertTrue(
                            ssimDiff > SSIM_THRESHOLD,
                            """Screenshots '$file1' and '$file2' should be different, but marked similar with difference $ssimDiff"""
                        )
                    }
                }
            }
        }
    }
}
