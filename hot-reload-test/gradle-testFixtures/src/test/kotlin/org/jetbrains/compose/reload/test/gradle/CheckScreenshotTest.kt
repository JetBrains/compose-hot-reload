/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.test.gradle

import org.jetbrains.skia.Image
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CheckScreenshotTest {
    private val screenshotsDir = this::class.java.classLoader.getResource("screenshots").toURI()

    fun <K, V> Map<K, V>.uniquePairs() = keys.toList().let { keys ->
        keys.indices.flatMap { i ->
            ((i + 1)..keys.lastIndex).map { j ->
                keys[i] to keys[j]
            }
        }
    }

    private inline fun onSimilarImages(body: (Map<String, Image>) -> Unit) {
        Path.of(screenshotsDir).resolve("similar").listDirectoryEntries()
            .forEach { onImages(it, body) }
    }

    private inline fun onDifferentImages(body: (Map<String, Image>) -> Unit) {
        Path.of(screenshotsDir).resolve("different").listDirectoryEntries()
            .forEach { onImages(it, body) }
    }

    private inline fun onImages(folder: Path, body: (Map<String, Image>) -> Unit) {
        val images = folder.listDirectoryEntries().associate { path ->
            path.toString().split('/').takeLast(2).joinToString("/") to path.readImage()
        }
        body(images)
    }

    @Test
    fun `test - averagePixelValueDiff`() {
        onSimilarImages { images ->
            for ((file1, file2) in images.uniquePairs()) {
                assertEquals(
                    0, countBadPixels(images[file1]!!, images[file1]!!),
                    "Screenshot '$file1' is not identical to itself"
                )
                assertEquals(
                    0, countBadPixels(images[file2]!!, images[file2]!!),
                    "Screenshot '$file2' is not identical to itself"
                )
                val badPixels = countBadPixels(images[file1]!!, images[file2]!!)
                assertEquals(
                    0, badPixels,
                    "Screenshots '$file1' and '$file2' should be similar, but marked different with value '$badPixels' bad pixels"
                )
            }
        }

        onDifferentImages { images ->
            for ((file1, file2) in images.uniquePairs()) {
                assertEquals(
                    0, countBadPixels(images[file1]!!, images[file1]!!),
                    "Screenshot '$file1' is not identical to itself"
                )
                assertEquals(
                    0, countBadPixels(images[file2]!!, images[file2]!!),
                    "Screenshot '$file2' is not identical to itself"
                )
                val badPixels = countBadPixels(images[file1]!!, images[file2]!!)
                assertTrue(
                    badPixels > 0,
                    "Screenshots '$file1' and '$file2' should be different, but marked similar with $badPixels bad pixels"
                )
            }
        }
    }
}
