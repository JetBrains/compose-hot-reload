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
import org.jetbrains.skia.Paint
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import kotlin.math.max

private val logger = createLogger()

class ImageDiff(
    val value: Float, val diff: Image
) {
    init {
        require(value in 0f..1f)
    }

    operator fun compareTo(other: ImageDiff): Int = value.compareTo(other.value)
}

fun imageDiff(expect: Image, actual: Image): ImageDiff {
    logger.info("Comparing image")
    val width = expect.width
    if (actual.width != width) throw IllegalArgumentException("Expected width: $width, actual: $actual")

    val height = expect.height
    if (actual.height != height) throw IllegalArgumentException("Expected height: $height, actual: $actual")

    fun Image.preprocess(): Image {
        return this.blurred(1f)
    }

    logger.trace { "Preprocessing image: $width x $height" }
    val expectProcessed = expect.preprocess()
    val actualProcessed = actual.preprocess()

    logger.trace { "Extracting channels" }
    val expectRed = expectProcessed.redChannel()
    val expectGreen = expectProcessed.greenChannel()
    val expectBlue = expectProcessed.blueChannel()

    val actualRed = actualProcessed.redChannel()
    val actualGreen = actualProcessed.greenChannel()
    val actualBlue = actualProcessed.blueChannel()

    logger.trace { "Calculating derivatives" }
    val expectRedDerivation = expectRed.derivative(2)
    val expectGreenDerivation = expectGreen.derivative(2)
    val expectBlueDerivation = expectBlue.derivative(2)

    val actualRedDerivation = actualRed.derivative(2)
    val actualGreenDerivation = actualGreen.derivative(2)
    val actualBlueDerivation = actualBlue.derivative(2)

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
            float diffDRVertical = abs(dExpectVertical - dActualVertical);
            
            // Inverted Exponential Decay 
            // https://www.desmos.com/calculator/9dxbfv1olz
            return 1 - exp(-4 *(diffDHorizontal + diffDRVertical) * diffValue);
        }
        
        half4 main(float2 coord) {
            float red = channel(coord, 0);
            float green = channel(coord, 1);
            float blue = channel(coord, 2);
            return half4(red, green, blue, 1.0);
        }
 
    """.trimIndent()

    logger.trace { "Calculating diff image" }
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

    logger.trace { "Finding 'maxValue'" }
    var maxValue = 0
    for (x in 0 until expect.width) {
        for (y in 0 until expect.height) {
            val color = bitmap.getColor(x, y)
            maxValue = max(maxValue, Color.getR(color))
            maxValue = max(maxValue, Color.getG(color))
            maxValue = max(maxValue, Color.getB(color))
        }
    }

    return ImageDiff(maxValue / 255f, Image.makeFromBitmap(bitmap))
}
