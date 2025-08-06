/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.test.gradle

import org.intellij.lang.annotations.Language
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skiko.toBufferedImage
import java.awt.image.BufferedImage
import java.nio.file.Path
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes

public fun Image.withPaint(paint: Paint): Image {
    val resultBitmap = Bitmap()
    resultBitmap.allocN32Pixels(this.width, this.height)
    val resultCanvas = Canvas(resultBitmap)
    resultCanvas.drawImage(this, 0f, 0f, paint)
    resultCanvas.close()
    return Image.makeFromBitmap(resultBitmap)
}

public fun Image.withPaint(paint: Paint.() -> Unit): Image =
    withPaint(Paint().apply(paint))


@Suppress("unused") // debugging utility!
private fun Image.toBufferedImage(): BufferedImage {
    return Bitmap.makeFromImage(this).toBufferedImage()
}

/**
 * Creates an image by subtracting the colors of the two images.
 * Two equal images will produce an entirely black image.
 */
@Suppress("unused") // debugging utility!
public fun diff(first: Image, second: Image): Image {
    @Language("GLSL")
    val diffShader = """
        uniform shader a;
        uniform shader b;
        
        half4 main(float2 coord) {
            half4 aColor = a.eval(coord);
            half4 bColor = b.eval(coord);
            return half4(abs(aColor.r - bColor.r), abs(aColor.g - bColor.g), abs(aColor.b - bColor.b), 1.0);
        }
    """.trimIndent()

    val shaderBuilder = RuntimeShaderBuilder(RuntimeEffect.makeForShader(diffShader))
    shaderBuilder.child("a", first.makeShader())
    shaderBuilder.child("b", second.makeShader())


    val resultBitmap = Bitmap()
    resultBitmap.allocN32Pixels(first.width, first.height)
    val resultCanvas = Canvas(resultBitmap)

    val paint = Paint()
    paint.shader = shaderBuilder.makeShader()

    resultCanvas.drawPaint(paint)
    resultCanvas.close()
    return Image.makeFromBitmap(resultBitmap)
}

/**
 * Compare two images, returning a 'comparison image' that is white at each given pixel
 * which is considered 'bad' (expect and actual images differ there).
 *
 * The comparison does not require pixel perfection (see [diff] for this).
 * Instead, each pixel of the [actual] image is compared to a given area (size controlled by [radius])
 * of the [expect] image. If the expect-area contains a pixel with lower values and one pixel
 * with higher values, then the pixel was matched (as part of a gradient).
 */
public fun compare(
    expect: Image, actual: Image,
    colorTolerance: Float = 0.01f,
    radius: Int = 3,
): Image {
    val width = expect.width
    require(actual.width == width) { "actual image width must be equal to expect image width" }

    val height = expect.height
    require(actual.height == height) { "actual image height must be equal to expect image height" }

    val builder = RuntimeShaderBuilder(compareShader(radius))
    builder.uniform("size", expect.width.toFloat(), expect.height.toFloat())
    builder.child("expect", expect.makeShader())
    builder.child("actual", actual.makeShader())
    builder.uniform("colorTolerance", colorTolerance)

    val resultBitmap = Bitmap()
    resultBitmap.allocN32Pixels(width, height)
    val resultCanvas = Canvas(resultBitmap)
    val paint = Paint()
    paint.shader = builder.makeShader()

    resultCanvas.drawPaint(paint)
    resultCanvas.close()
    return Image.makeFromBitmap(resultBitmap)
}

private val compareShaders = hashMapOf<Int, RuntimeEffect>()

private fun compareShader(radius: Int): RuntimeEffect {
    return compareShaders.getOrPut(radius) {
        newCompareShader(radius)
    }
}

@Language("GLSL")
private fun newCompareShader(radius: Int) = RuntimeEffect.makeForShader(
    """
    uniform float2 size;
    uniform shader expect;
    uniform shader actual;
    uniform float colorTolerance;
    const int radius = $radius;
    
    half4 main(float2 coord) {
        half4 actualColor = actual.eval(coord);
        
        half4 lowerBound = actualColor - colorTolerance;
        half4 upperBound = actualColor + colorTolerance;
        
        half4 lowColor = half4(1.0, 1.0, 1.0, 1.0);
        half4 highColor = half4(0.0, 0.0, 0.0, 1.0);
        
        for(int dx = -radius; dx <= radius; dx++) {
            for(int dy  = -radius; dy <= radius; dy++) {
                float2 targetCoord = coord + half2(dx, dy);
                if(targetCoord.x <= 0 || targetCoord.x >= size.x || targetCoord.y <= 0 || targetCoord.y >= size.y) {
                    continue;
                }
                
                half4 expectColor = expect.eval(targetCoord);
                
                lowColor = half4(
                    min(lowColor.r, expectColor.r),
                    min(lowColor.g, expectColor.g),
                    min(lowColor.b, expectColor.b),
                    min(lowColor.a, expectColor.a)
                );
                
                highColor = half4(
                    max(highColor.r, expectColor.r),
                    max(highColor.g, expectColor.g),
                    max(highColor.b, expectColor.b),
                    max(highColor.a, expectColor.a)
                );
                
                if(all(greaterThanEqual(highColor, lowerBound)) && all(lessThanEqual(lowColor, upperBound))) {
                    return half4(0, 0, 0, 1.0);
                }
            }
        }

        return half4(1.0, 1.0, 1.0, 1.0);;
    }
""".trimIndent()
)

public fun Path.readImage(): Image {
    return readBytes().readImage()
}

public fun ByteArray.readImage(): Image {
    val codec = Codec.makeFromData(Data.makeFromBytes(this))
    val bitmap = Bitmap()
    bitmap.allocN32Pixels(codec.width, codec.height)
    codec.readPixels(bitmap)
    return Image.makeFromBitmap(bitmap)
}

public fun Path.writeImage(image: Image) {
    writeBytes(image.encodeToData()!!.bytes)
}
