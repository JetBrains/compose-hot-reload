import org.jetbrains.compose.reload.core.testFixtures.imageDiff
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Color
import org.jetbrains.skia.Font
import org.jetbrains.skia.FontEdging
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

class ImageDiffTest {

    @Test
    fun `test - red vs green`() {
        val expect = createImage {
            drawPaint(Paint().apply {
                color = Color.RED
            })
        }

        val actual = createImage {
            drawPaint(Paint().apply {
                color = Color.GREEN
            })
        }

        val diff = imageDiff(expect, actual)
        diff.score
    }

    @Test
    fun `test - light red vs dark red`() {
        val expect = createImage {
            drawPaint(Paint().apply {
                color = Color.makeRGB(200, 0, 0)
            })
        }

        val actual = createImage {
            drawPaint(Paint().apply {
                color = Color.makeRGB(198, 0, 0)
            })
        }

        val diff = imageDiff(expect, actual)
        assertTrue(diff.isDifferent(), "score=${diff.score}")
    }

    @Test
    fun `test - font - anti alias on vs anti alias off`() {
        val expectFont = Font()
        expectFont.size = 96f
        expectFont.edging = FontEdging.ANTI_ALIAS

        val expect = createImage {
            drawString("Hello", 128f, 128f, expectFont, Paint().apply {
                isAntiAlias = true
            })
        }

        val actualFont = Font()
        actualFont.size = 96f
        actualFont.edging = FontEdging.ALIAS
        actualFont.isSubpixel = false
        val actual = createImage {
            drawString("Hello", 128f, 128f, actualFont, Paint().apply {
                isAntiAlias = false
            })
        }

        val diff = imageDiff(expect, actual)
        assertTrue(diff.isSimilar(), "score=${diff.score}")
    }

    @Test
    fun `test - small dot`() {
        val expect = createImage {
            drawPaint(Paint().apply {
                color = Color.WHITE
            })
        }

        val actual = createImage {
            drawPaint(Paint().apply {
                color = Color.WHITE
            })

            drawCircle(128f, 128f, 2f, Paint().apply {
                color = Color.makeRGB(200, 200, 200) // barely noticeable: Also almost white
            })
        }

        val diff = imageDiff(expect, actual)
        assertTrue(diff.isDifferent(), "score=${diff.score}")
    }


    private fun createImage(width: Int = 600, height: Int = 800, draw: Canvas.() -> Unit): Image {
        val bitmap = Bitmap()
        bitmap.allocN32Pixels(width, height)
        val canvas = Canvas(bitmap)
        draw(canvas)
        canvas.close()

        return Image.makeFromBitmap(bitmap)
    }
}
