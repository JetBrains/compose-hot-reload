/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.core.testFixtures

import org.intellij.lang.annotations.Language
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Codec
import org.jetbrains.skia.ColorFilter
import org.jetbrains.skia.ColorMatrix
import org.jetbrains.skia.Data
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skiko.toBufferedImage
import java.awt.image.BufferedImage
import java.nio.file.Path
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes
import kotlin.math.roundToInt

fun Image.redChannel(): Image = withColorMatrix(
    ColorMatrix(
        1f, 0f, 0f, 0f, 0f,
        1f, 0f, 0f, 0f, 0f,
        1f, 0f, 0f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )
)

fun Image.greenChannel(): Image = withColorMatrix(
    ColorMatrix(
        0f, 1f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )
)

fun Image.blueChannel(): Image = withColorMatrix(
    ColorMatrix(
        0f, 0f, 1f, 0f, 0f,
        0f, 0f, 1f, 0f, 0f,
        0f, 0f, 1f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )
)

fun Image.withColorMatrix(colorMatrix: ColorMatrix): Image = withPaint {
    colorFilter = ColorFilter.makeMatrix(colorMatrix)
}

fun Image.withPaint(paint: Paint): Image {
    val resultBitmap = Bitmap()
    resultBitmap.allocN32Pixels(this.width, this.height)
    val resultCanvas = Canvas(resultBitmap)
    resultCanvas.drawImage(this, 0f, 0f, paint)
    resultCanvas.close()
    return Image.makeFromBitmap(resultBitmap)
}

fun Image.withPaint(paint: Paint.() -> Unit): Image =
    withPaint(Paint().apply(paint))

fun Image.blurred(radius: Float): Image = withPaint {
    imageFilter = ImageFilter.makeBlur(radius, radius, mode = FilterTileMode.CLAMP)
}

fun Image.scaled(factor: Float): Image {
    val newWidth = (width * factor).roundToInt()
    val newHeight = (height * factor).roundToInt()

    val resultBitmap = Bitmap()
    resultBitmap.allocPixels(ImageInfo.makeN32Premul(newWidth, newHeight))
    val resultCanvas = Canvas(resultBitmap)

    resultCanvas.drawImageRect(
        image = this,
        src = Rect.makeWH(width.toFloat(), height.toFloat()),
        dst = Rect.makeWH(newWidth.toFloat(), newHeight.toFloat()),
        samplingMode = SamplingMode.MITCHELL,
        paint = Paint(),
        strict = false
    )

    resultCanvas.close()
    return Image.makeFromBitmap(resultBitmap)
}

@Suppress("unused") // debugging utility!
private fun Image.toBufferedImage(): BufferedImage {
    return Bitmap.makeFromImage(this).toBufferedImage()
}


/**
 * Creates an image by subtracting the colors of the two images.
 * Two equal images will produce an entirely black image.
 */
fun diff(first: Image, second: Image): Image {
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
 * Creates an image, which will be a derivative of the original image. (Only the first channel will be derived)
 * The horizontal change component (dr/dx) will be encoded in the red channel.
 * The vertical change component (dr/dy) will be encoded in the green channel.
 *
 * The derivation is normed so that a value of 1 means hard change in the direction of the axis.
 * A value of 0 means a hard change in the opposite direction of the axis.
 * A value of .5 means no change in the direction of the axis.
 *
 * The blue channel will be filled with .5, producing a gray area when no pixel values change.
 *
 * @param size: The sample size to calculate the derivative. The larger this value, the bigger the area
 * which will be used to calculate the pixel value change.
 *
 */
fun Image.derivative(size: Int): Image {
    @Language("GLSL")
    val shaderCode = """
        uniform shader content;
        uniform float2 imageSize;
        
        float2 clamp_coord(float2 coord) {
            return clamp(coord, float2(1, 1), imageSize - 1.0);
        }

        
        half4 main(float2 coord) {
            // left 
            half4 left = half4(0.0, 0.0, 0.0, 1.0);
            for(int x = -$size; x < 0; x++) {
                for(int y = -$size; y <= $size; y++){
                    left += content.eval(clamp_coord(coord + half2(x, y)));
                }
            }
            left /= ($size * $size + 1);
        
            // right
            half4 right = half4(0.0, 0.0, 0.0, 1.0);
            for(int x = 1; x <= $size; x++) {
                for(int y = -$size; y <= $size; y++){
                    right += content.eval(clamp_coord(coord + half2(x, y)));
                }
            }
            right /= ($size * $size + 1);
            half4 horizontal = (left / 2 - right / 2) + 0.5;
            
            // top
            half4 top = half4(0.0, 0.0, 0.0, 1.0);
            for(int x = -$size; x <= $size; x++){
                for(int y = -$size; y < 0; y++) {
                    top += content.eval(clamp_coord(coord + half2(x, y)));
                }
            }
            top /= ($size * $size + 1);
            
            
            // bottom
            half4 bottom = half4(0.0, 0.0, 0.0, 1.0);
            for(int x = -$size; x <= $size; x++){
                for(int y = 1; y <= $size; y++) {
                    bottom += content.eval(clamp_coord(coord + half2(x, y)));
                }
            }
            bottom /= ($size * $size + 1);
            half4 vertical = (top / 2 - bottom / 2) + 0.5;
            
            return half4(horizontal.r, vertical.r, 0.5, 1.0);
        }
    """.trimIndent()


    val resultBitmap = Bitmap()
    resultBitmap.allocN32Pixels(width, height)
    val resultCanvas = Canvas(resultBitmap)
    val paint = Paint()

    paint.imageFilter = ImageFilter.makeRuntimeShader(
        runtimeShaderBuilder = RuntimeShaderBuilder(
            RuntimeEffect.makeForShader(shaderCode)
        ).apply {
            uniform("imageSize", width.toFloat(), height.toFloat())
        }, "content", null
    )

    resultCanvas.drawImage(this, 0f, 0f, paint)
    resultCanvas.close()
    return Image.makeFromBitmap(resultBitmap)
}


fun Path.readImage(): Image {
    val codec = Codec.makeFromData(Data.makeFromBytes(readBytes()))
    val bitmap = Bitmap()
    bitmap.allocN32Pixels(codec.width, codec.height)
    codec.readPixels(bitmap)
    return Image.makeFromBitmap(bitmap)
}

fun Path.writeImage(image: Image) {
    writeBytes(image.encodeToData()!!.bytes)
}
