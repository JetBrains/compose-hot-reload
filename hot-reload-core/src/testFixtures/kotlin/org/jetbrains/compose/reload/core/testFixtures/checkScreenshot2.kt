/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.core.testFixtures

import org.intellij.lang.annotations.Language
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.info
import org.jetbrains.compose.reload.core.trace
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Color
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.Paint
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import kotlin.math.max

private val logger = createLogger()

class ImageDiff(
    val score: Float, val diffImage: Image
) {
    init {
        require(score in 0f..1f)
    }

    companion object {
        const val THRESHOLD = 0.75
    }

    fun isDifferent(): Boolean = score >= THRESHOLD
    fun isSimilar(): Boolean = !isDifferent()
}

fun imageDiff(expect: Image, actual: Image): ImageDiff {
    logger.info("Comparing image")
    val width = expect.width
    if (actual.width != width) throw IllegalArgumentException("Expected width: $width, actual: $actual")

    val height = expect.height
    if (actual.height != height) throw IllegalArgumentException("Expected height: $height, actual: $actual")

    fun Image.preprocess(): Image {
        return blurred(1f)
    }

    logger.trace { "Preprocessing image: $width x $height" }
    val expectProcessed = expect.preprocess()
    val actualProcessed = actual.preprocess()

    val expectSmall = expectProcessed.scaled(.25f)
    val actualSmall = actualProcessed.scaled(.25f)

    logger.trace { "Extracting channels" }
    val expectRed = expectSmall.redChannel()
    val expectGreen = expectSmall.greenChannel()
    val expectBlue = expectSmall.blueChannel()

    val actualRed = actualSmall.redChannel()
    val actualGreen = actualSmall.greenChannel()
    val actualBlue = actualSmall.blueChannel()

    logger.trace { "Calculating derivatives" }
    val expectRedDerivation = expectRed.derivative(2).scaled(4f)
    val expectGreenDerivation = expectGreen.derivative(2).scaled(4f)
    val expectBlueDerivation = expectBlue.derivative(2).scaled(4f)

    val actualRedDerivation = actualRed.derivative(2).scaled(4f)
    val actualGreenDerivation = actualGreen.derivative(2).scaled(4f)
    val actualBlueDerivation = actualBlue.derivative(2).scaled(4f)

    @Language("GLSL")
    val diffShader = """
        uniform shader expect;
        uniform shader actual;
        
        uniform shader drExpect;
        uniform shader dgExpect;
        uniform shader dbExpect;
        
        uniform shader drActual;
        uniform shader dgActual;
        uniform shader dbActual;
        
        float getChannel(half4 color, int index) {
            return index == 0 ? color.r :
                   index == 1 ? color.g :
                   index == 2 ? color.b :
                                color.a;
        }
        
        float channel(float2 coord, int channel) {
            half4 expectColor = expect.eval(coord);
            half4 actualColor = actual.eval(coord);
            
            // red 
            float expectValue = getChannel(expectColor, channel);
            float actualValue = getChannel(actualColor, channel);
            float diffValue = abs(expectValue - actualValue);
            
            half4 dExpect = drExpect.eval(coord);
            float dExpectHorizontal = getChannel(dExpect, 0) - 0.5;
            float dExpectVertical = getChannel(dExpect, 1) - 0.5;
            
            half4 dActual = drActual.eval(coord);
            float dActualHorizontal = getChannel(dActual, 0) - 0.5;
            float dActualVertical = getChannel(dActual, 1) - 0.-5;
            
             
            float diffDHorizontal = abs(dExpectHorizontal - dActualHorizontal);
            float diffDVertical = abs(dExpectVertical - dActualVertical);
            
            // The penalty function can be played around with here:
            // https://www.desmos.com/calculator/lh6rdljul0
            
            // Logistic Function for high penalty of diffD (pivot point is 0.2)
            float horizontalK = 1/(1 + exp(-12 *(diffDHorizontal - 0.2)));
            float verticalK = 1/(1 + exp(-12 *(diffDVertical - 0.2)));
            
            // Inverted Exponential Decay 
            return (1 - exp(-1 * 4 *(horizontalK + verticalK + 0.1) * diffValue)) * 1.5;
        }
        
        half4 main(float2 coord) {
            float red = channel(coord, 0);
            float green = channel(coord, 1);
            float blue = channel(coord, 2);
            return half4(red, green, blue, 1.0);
        }
 
    """.trimIndent()

    logger.trace { "Calculating diff image" }
    val rawDiffImage = run {
        val shader = RuntimeShaderBuilder(RuntimeEffect.makeForShader(diffShader)).apply {
            child("expect", expectProcessed.makeShader())
            child("actual", actualProcessed.makeShader())
            child("drExpect", expectRedDerivation.makeShader())
            child("dgExpect", expectGreenDerivation.makeShader())
            child("dbExpect", expectBlueDerivation.makeShader())
            child("drActual", actualRedDerivation.makeShader())
            child("dgActual", actualGreenDerivation.makeShader())
            child("dbActual", actualBlueDerivation.makeShader())
        }

        val paint = Paint()
        paint.shader = shader.makeShader()

        val bitmap = Bitmap()
        bitmap.allocN32Pixels(expect.width, expect.height)

        val canvas = Canvas(bitmap)
        canvas.drawPaint(paint)
        Image.makeFromBitmap(bitmap)
    }

    logger.trace { "Integrating diff image" }
    val integratedDiffImage = integrateDiff(rawDiffImage)
    val integratedDiffBitmap = Bitmap.makeFromImage(integratedDiffImage)

    logger.trace { "Finding 'maxValue'" }
    var maxValue = 0
    for (x in 0 until expect.width) {
        for (y in 0 until expect.height) {
            val color = integratedDiffBitmap.getColor(x, y)
            maxValue = max(maxValue, Color.getR(color))
            maxValue = max(maxValue, Color.getG(color))
            maxValue = max(maxValue, Color.getB(color))
        }
    }
    val score = maxValue / 255f;
    logger.trace { "maxValue = $maxValue; score= $score" }

    return ImageDiff(score, integratedDiffImage)
}

private fun integrateDiff(image: Image, size: Int = 2): Image {
    /*
    Inspired by calculating probabilities:
    Let's treat each pixel value (0.0-1.0) as the probability of the image being different.
    In this function we can look at nearby pixels (rect) and use their probability as well.

    If each pixel's probability is known, then the probability of multiple pixels indicating that the image
    is different is 1 - (1 - p1) * (1 - p2) * ... * (1 - pn)
     */
    @Language("GLSL")
    val shaderCode = """
        uniform shader image;
        half4 main(float2 coord) {
            half4 runningProduct = half4(1.0, 1.0, 1.0, 1.0);
            for(int dx = -$size; dx <= $size; dx++) {
               for(int dy = -$size; dy <= $size; dy++) {
                   float2 sampleCoord = coord + half2(dx, dy);
                   if(sampleCoord.x <= 0 || sampleCoord.x >= ${image.width} || sampleCoord.y <= 0 || sampleCoord.y >= ${image.height}) {
                       continue;
                   }
                   runningProduct *= (1 - image.eval(sampleCoord));
               }
            }
            
            return 1- runningProduct;
        }
    """.trimIndent()

    val resultBitmap = Bitmap()
    resultBitmap.allocN32Pixels(image.width, image.height)
    val resultCanvas = Canvas(resultBitmap)
    val paint = Paint()
    paint.imageFilter = ImageFilter.makeRuntimeShader(
        runtimeShaderBuilder = RuntimeShaderBuilder(RuntimeEffect.makeForShader(shaderCode)), "image", null
    )
    resultCanvas.drawImage(image, 0f, 0f, paint)
    resultCanvas.close()
    return Image.makeFromBitmap(resultBitmap)
}
