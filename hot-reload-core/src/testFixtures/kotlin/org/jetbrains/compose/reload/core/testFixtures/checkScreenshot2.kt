/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.core.testFixtures

import org.intellij.lang.annotations.Language
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.Paint
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skiko.toBufferedImage

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
    val expectRed = expect.redChannel()
    val expectGreen = expect.greenChannel()
    val expectBlue = expect.blueChannel()

    val actualRed = actual.redChannel()
    val actualGreen = actual.greenChannel()
    val actualBlue = actual.blueChannel()

    val expectRedDerivation = expectRed.derivative(3)
    val expectGreenDerivation = expectGreen.derivative(3)
    val expectBlueDerivation = expectBlue.derivative(3)

    val actualRedDerivation = actualRed.derivative(3)
    val actualGreenDerivation = actualGreen.derivative(3)
    val actualBlueDerivation = actualBlue.derivative(3)

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
        
        half4 main(float2 coord) {
            half4 expectColor = expect.eval(coord);
            half4 actualColor = actual.eval(coord);
            
            // red 
            float eR = expectColor.r;
            float aR = actualColor.r;
            float diffR = abs(eR - aR);
            
            half4 drE = drExpect.eval(coord);
            float drEHorizontal = drE.r - 0.5;
            float drEVertical = drE.g - 0.5;
            
            half4 drA = drActual.eval(coord);
            float drAHorizontal = drA.r - 0.5;
            float drAVertical = drA.g - 0.-5;
            
             
            float diffDRHorizontal = abs(drEHorizontal - drAHorizontal);
            float diffDRVertical = abs(drEVertical - drAVertical);
            
            // Inverted Exponential Decay 
            // https://www.desmos.com/calculator/9dxbfv1olz
            float redValue = 1 - exp(-12 *(diffDRHorizontal + diffDRVertical) * diffR);
            
          
            return half4(redValue, 0.0, 0.0, 1.0);
            
            
            // green
            
            
            // blue
        }
        
 
    """.trimIndent()

    val shader = RuntimeShaderBuilder(RuntimeEffect.makeForShader(diffShader)).apply {
        child("expect", expect.makeShader())
        child("actual", actual.makeShader())
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

    val diffImage = bitmap.toBufferedImage()
    println(diffImage)
    return ImageDiff.zero
}

fun integrateDiff(diffImage: Image): Image {
    @Language("GLSL")
    val integrateShader = """
        uniform shader content;
        
        half4 main(float2 coord) {
            half4 sum = half4(0.0, 0.0, 0.0, 1.0);
            for(int x = -2; x <= 2; x++) {
                for(int y = -2; y <= 2; y++) {
                    sum += content.eval(coord + half2(x, y));
                }
            }
        return sum;   
}
    """.trimIndent()


    val resultBitmap = Bitmap()
    resultBitmap.allocN32Pixels(diffImage.width, diffImage.height)
    val resultCanvas = Canvas(resultBitmap)

    val paint = Paint()
    paint.imageFilter = ImageFilter.makeRuntimeShader(
        runtimeShaderBuilder = RuntimeShaderBuilder(
            RuntimeEffect.makeForShader(integrateShader)
        ), "content", null
    )

    resultCanvas.drawImage(diffImage, 0f, 0f, paint)
    resultCanvas.close()

    return Image.makeFromBitmap(resultBitmap)
}
