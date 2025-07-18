/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.test.gradle

import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.BenchmarkMode
import kotlinx.benchmark.Blackhole
import kotlinx.benchmark.Measurement
import kotlinx.benchmark.Mode
import kotlinx.benchmark.OutputTimeUnit
import kotlinx.benchmark.Param
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import kotlinx.benchmark.TearDown
import kotlinx.benchmark.Warmup
import java.awt.image.BufferedImage
import java.awt.image.BufferedImage.TYPE_INT_RGB
import java.util.concurrent.TimeUnit
import kotlin.io.path.ExperimentalPathApi
import kotlin.random.Random

@State(Scope.Benchmark)
@Measurement(iterations = 5, time = 3, timeUnit = TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@BenchmarkMode(Mode.AverageTime)
open class CheckScreenshotBenchmark {

    @Param("256", "512")
    var imageSize = 0

    @Param("500")
    var numberOfImages = 0
    
    @Param("25", "50", "100")
    var imageProportion = 0

    val expectedImages = mutableListOf<BufferedImage>()
    val actualImages = mutableListOf<BufferedImage>()

    @Setup
    fun generateImages() {
        fun generateImage(random: Random, size: Int, imageProportion: Int = 100): BufferedImage {
            val image = BufferedImage(size, size, TYPE_INT_RGB)
            
            val imageArea = when (imageProportion) {
                100 -> size
                else -> (size * (imageProportion.toFloat() / 100)).toInt()
            }
            
            for (x in 0 ..< size) {
                for (y in 0 ..< size) {
                    val rgb = if (x < imageArea && y < imageArea) {
                        ((random.nextInt(256) and 0xFF) shl 16) or
                            ((random.nextInt(256) and 0xFF) shl 8) or
                            ((random.nextInt(256) and 0xFF) shl 0)
                    } else {
                        0xFFFFFF
                    }
                    image.setRGB(x, y, rgb)
                }
            }
            return image
        }

        val random = Random(0x123)
        repeat(numberOfImages) {
            val expected = generateImage(random, imageSize, imageProportion)
            expectedImages += expected
            actualImages += when {
                random.nextBoolean() -> expected
                else -> generateImage(random, imageSize, imageProportion)
            }
        }
    }

    @Benchmark
    fun pixelAverage(blackhole: Blackhole) {
        repeat(numberOfImages) {
            blackhole.consume(averagePixelValueDiff(expectedImages[it], actualImages[it]))
        }
    }

    @OptIn(ExperimentalPathApi::class)
    @TearDown
    fun cleanup() {
        expectedImages.clear()
        actualImages.clear()
    }
}
