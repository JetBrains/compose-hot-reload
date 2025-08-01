/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.core.testFixtures

import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Color
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skiko.toBufferedImage
import java.awt.image.BufferedImage
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

@JvmInline
value class ImageDiff(val value: Float) {
    init {
        require(value in 0f..1f)
    }

    companion object {
        val zero = ImageDiff(0f)
        val full = ImageDiff(1f)
    }

    operator fun compareTo(other: ImageDiff): Int = value.compareTo(other.value)
}

fun imageDiff(expect: Image, actual: Image): ImageDiff {
    val width = expect.width
    if (actual.width != width) return ImageDiff.full

    val height = expect.height
    if (actual.height != height) return ImageDiff.full

    return colorValueDiff(expect, actual)
}

private fun colorValueDiff(expect: Image, actual: Image): ImageDiff {
    fun preprocess(image: Image): Image {
        return image.blurred(9f).scaled(.3f)
    }

    val expectProcessed = preprocess(expect)
    val actualProcessed = preprocess(actual)

    val diffImage = valueDiffImage(expectProcessed, actualProcessed)
    val diffImageIntegrated = diffImage.kernelIntegrate(3)

    var maxDiff = 0f
    for (x in 0 until diffImageIntegrated.width) {
        for (y in 0 until diffImageIntegrated.height) {
            val pixel = diffImageIntegrated.getColor(x, y)
            maxDiff = maxOf(maxDiff, Color.getR(pixel) / 255f)
            maxDiff = maxOf(maxDiff, Color.getG(pixel) / 255f)
            maxDiff = maxOf(maxDiff, Color.getB(pixel) / 255f)
        }
    }

    return ImageDiff(maxDiff)
}

private fun edgeValueDiff(expect: Image, actual: Image): ImageDiff {
    return ImageDiff.zero
}


private fun Image.blurred(radius: Float): Image {
    val blur = ImageFilter.makeBlur(radius, radius, mode = FilterTileMode.CLAMP)
    val result = Bitmap()
    result.allocPixels(ImageInfo.makeN32Premul(width, height))
    val canvas = Canvas(result)
    val paint = Paint()
    paint.imageFilter = blur
    canvas.drawImage(this, 0f, 0f, paint)
    canvas.close()
    return Image.makeFromBitmap(result)
}


private fun Image.scaled(factor: Float): Image {
    val newWidth = (width * factor).roundToInt()
    val newHeight = (height * factor).roundToInt()

    val resultBitmap = Bitmap()
    resultBitmap.allocPixels(ImageInfo.makeN32Premul(newWidth, newHeight))
    val resultCanvas = Canvas(resultBitmap)

    resultCanvas.drawImageRect(
        image = this,
        src = Rect.makeWH(width.toFloat(), height.toFloat()),
        dst = Rect.makeWH(newWidth.toFloat(), newHeight.toFloat()),
        samplingMode = SamplingMode.LINEAR,
        paint = Paint(),
        strict = false
    )

    resultCanvas.close()
    return Image.makeFromBitmap(resultBitmap)
}

private fun valueDiffImage(a: Image, b: Image): Image {
    require(a.width == b.width && a.height == b.height) { "Images must have the same dimensions" }

    val resultData = ByteArray(a.width * a.height * 4)
    val resultBitmap = Bitmap()
    resultBitmap.allocN32Pixels(a.width, a.height)

    val aBitmap = Bitmap.makeFromImage(a)
    val bBitmap = Bitmap.makeFromImage(b)

    for (x in 0 until a.width) {
        for (y in 0 until a.height) {
            val aPixel = aBitmap.getColor(x, y)
            val bPixel = bBitmap.getColor(x, y)

            val red = (Color.getR(aPixel) - Color.getR(bPixel)).absoluteValue
            val green = (Color.getG(aPixel) - Color.getG(bPixel)).absoluteValue
            val blue = (Color.getB(aPixel) - Color.getB(bPixel)).absoluteValue

            val pixelIndex = (y * a.width + x) * 4
            resultData[pixelIndex + 3] = 255.toByte()
            resultData[pixelIndex + 2] = red.toByte()
            resultData[pixelIndex + 1] = green.toByte()
            resultData[pixelIndex] = blue.toByte()
        }
    }

    resultBitmap.installPixels(resultData)
    return Image.makeFromBitmap(resultBitmap)
}


private fun Image.kernelIntegrate(size: Int): Bitmap {
    val resultBitmap = Bitmap()
    resultBitmap.allocN32Pixels(width, height)
    val resultCanvas = Canvas(resultBitmap)

    val convolution = ImageFilter.makeMatrixConvolution(
        kernelW = size, kernelH = size,
        kernel = FloatArray(size * size) { 1f },
        gain = 1f / size.toFloat(), bias = 0f,
        offsetX = size / 2, offsetY = size / 2,
        tileMode = FilterTileMode.CLAMP,
        convolveAlpha = false,
        input = null,
        crop = null,
    )

    resultCanvas.drawImage(this, 0f, 0f, Paint().apply {
        imageFilter = convolution
    })

    resultCanvas.close()
    return resultBitmap
}


@Suppress("unused") // debugging utility!
private fun Image.toBufferedImage() : BufferedImage {
    return Bitmap.makeFromImage(this).toBufferedImage()
}
