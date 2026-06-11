/*
 * Copyright 2024-2026 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.jvm

import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.NativeLong
import com.sun.jna.Pointer
import com.sun.jna.Structure
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.info
import org.jetbrains.compose.reload.core.warn
import java.awt.Window
import java.awt.image.BufferedImage
import kotlin.math.abs

private val logger = createLogger()

/**
 * Captures a window on macOS using CoreGraphics, even when the window is obscured by other windows.
 *
 * [java.awt.Robot.createScreenCapture] reads pixels off the actual screen, so anything covering the
 * window ends up in the screenshot. CoreGraphics' `CGWindowListCreateImage` instead renders the
 * window's own backing store by its window id, which works regardless of what is in front of it.
 *
 * The flow is:
 *  1. Enumerate on-screen windows ([CoreGraphics.CGWindowListCopyWindowInfo]) and find the one that
 *     belongs to this process (matching pid) and whose bounds match the AWT [window].
 *  2. Capture that window by id ([CoreGraphics.CGWindowListCreateImage]).
 *  3. Convert the resulting `CGImage` into a [BufferedImage].
 *
 * Returns `null` (after logging a reason) if anything goes wrong, so the caller can fall back to the
 * [java.awt.Robot] based capture.
 *
 * Note: `CGWindowListCreateImage` requires the Screen Recording permission on macOS 10.15+; without
 * it the call returns a null image. It's been deprecated since macOS 14 but still functional and remains
 * the simplest synchronous way to capture a specific window.
 */
internal fun captureWindowMacOs(window: Window): BufferedImage? {
    return try {
        val windowId = findWindowId(window)
        if (windowId == null) {
            logger.warn("macOS screenshot: could not find a native window matching the AWT window; falling back")
            return null
        }

        captureWindowImage(windowId)
    } catch (t: Throwable) {
        logger.warn("macOS screenshot: native capture failed; falling back", t)
        null
    }
}

/**
 * Finds the CoreGraphics window id for the given AWT [window] by enumerating the on-screen windows
 * owned by this process and matching their bounds against the AWT window's bounds.
 */
private fun findWindowId(window: Window): Int? {
    val pid = ProcessHandle.current().pid().toInt()

    val location = window.locationOnScreen
    val targetX = location.x.toDouble()
    val targetY = location.y.toDouble()
    val targetWidth = window.width.toDouble()
    val targetHeight = window.height.toDouble()

    val windowList = CoreGraphics.INSTANCE.CGWindowListCopyWindowInfo(
        kCGWindowListOptionOnScreenOnly or kCGWindowListExcludeDesktopElements, kCGNullWindowID
    ) ?: run {
        logger.warn("macOS screenshot: CGWindowListCopyWindowInfo returned null")
        return null
    }

    try {
        val count = CoreFoundation.INSTANCE.CFArrayGetCount(windowList).toInt()

        var bestId: Int? = null
        var bestScore = Double.MAX_VALUE
        var candidates = 0

        for (i in 0 until count) {
            val info = CoreFoundation.INSTANCE.CFArrayGetValueAtIndex(windowList, NativeLong(i.toLong())) ?: continue

            val ownerPid = readCFInt(info, kCGWindowOwnerPID) ?: continue
            if (ownerPid != pid) continue
            candidates++

            val boundsDict = CoreFoundation.INSTANCE.CFDictionaryGetValue(info, kCGWindowBounds) ?: continue
            val bounds = CGRect()
            if (CoreGraphics.INSTANCE.CGRectMakeWithDictionaryRepresentation(boundsDict, bounds).toInt() == 0) continue

            val score = abs(bounds.origin.x - targetX) + abs(bounds.origin.y - targetY) +
                abs(bounds.size.width - targetWidth) + abs(bounds.size.height - targetHeight)

            if (score < bestScore) {
                val windowNumber = readCFInt(info, kCGWindowNumber) ?: continue
                bestScore = score
                bestId = windowNumber
            }
        }

        logger.info(
            "macOS screenshot: pid=$pid, windows=$count, candidates(pid match)=$candidates, " +
                "bestWindowId=$bestId, bestScore=$bestScore, " +
                "awtBounds=[${targetX.toInt()},${targetY.toInt()} ${targetWidth.toInt()}x${targetHeight.toInt()}]"
        )

        if (bestId == null || bestScore > windowBoundsMatchTolerance) {
            return null
        }
        return bestId
    } finally {
        CoreFoundation.INSTANCE.CFRelease(windowList)
    }
}

/**
 * Captures the window with the given [windowId] and converts the `CGImage` into a [BufferedImage].
 */
private fun captureWindowImage(windowId: Int): BufferedImage? {
    val image = CoreGraphics.INSTANCE.CGWindowListCreateImage(
        cgRectNull(), kCGWindowListOptionIncludingWindow, windowId, kCGWindowImageBoundsIgnoreFraming
    )
    if (image == null) {
        logger.warn(
            "macOS screenshot: CGWindowListCreateImage returned null for window $windowId " +
                "(is the Screen Recording permission granted for this process?)"
        )
        return null
    }

    try {
        val width = CoreGraphics.INSTANCE.CGImageGetWidth(image).toInt()
        val height = CoreGraphics.INSTANCE.CGImageGetHeight(image).toInt()
        if (width <= 0 || height <= 0) {
            logger.warn("macOS screenshot: captured image has invalid size ${width}x$height")
            return null
        }

        val colorSpace = CoreGraphics.INSTANCE.CGColorSpaceCreateDeviceRGB()
        try {
            val bytesPerRow = width * 4
            val buffer = Memory(height.toLong() * bytesPerRow)

            val context = CoreGraphics.INSTANCE.CGBitmapContextCreate(
                buffer, NativeLong(width.toLong()), NativeLong(height.toLong()),
                NativeLong(8), NativeLong(bytesPerRow.toLong()), colorSpace,
                kCGImageAlphaNoneSkipFirst or kCGBitmapByteOrder32Little
            ) ?: run {
                logger.warn("macOS screenshot: CGBitmapContextCreate returned null")
                return null
            }

            try {
                CoreGraphics.INSTANCE.CGContextDrawImage(context, CGRect.ByValue(0.0, 0.0, width.toDouble(), height.toDouble()), image)

                /*
                 * The bitmap is little-endian XRGB, i.e. the bytes in memory are B,G,R,X. Read as native
                 * (little-endian) ints, each pixel becomes 0xXXRRGGBB, which is exactly the layout that
                 * BufferedImage.TYPE_INT_RGB expects (the high byte is ignored).
                 */
                val pixels = buffer.getIntArray(0, width * height)
                val result = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
                result.setRGB(0, 0, width, height, pixels, 0, width)
                logger.info("macOS screenshot: captured window $windowId as ${width}x$height image")
                return result
            } finally {
                CoreGraphics.INSTANCE.CGContextRelease(context)
            }
        } finally {
            CoreGraphics.INSTANCE.CGColorSpaceRelease(colorSpace)
        }
    } finally {
        CoreGraphics.INSTANCE.CGImageRelease(image)
    }
}

/** Reads an [Int] value out of a `CFNumber` stored under [key] in the [dict] `CFDictionary`. */
private fun readCFInt(dict: Pointer, key: Pointer): Int? {
    val number = CoreFoundation.INSTANCE.CFDictionaryGetValue(dict, key) ?: return null
    val out = Memory(4)
    if (CoreFoundation.INSTANCE.CFNumberGetValue(number, kCFNumberSInt32Type, out).toInt() == 0) return null
    return out.getInt(0)
}

/** The CoreGraphics null rect (infinite origin); passed to capture the window's full bounds. */
private fun cgRectNull(): CGRect.ByValue =
    CGRect.ByValue(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, 0.0, 0.0)

/**
 * Maximum accepted sum of absolute differences (in points, across x/y/width/height) between the AWT
 * window bounds and a candidate native window's bounds. Keeps us from capturing an unrelated window
 * of the same process if no good match exists.
 */
private const val windowBoundsMatchTolerance = 20.0

/* CoreGraphics constants */
private const val kCGNullWindowID = 0
private const val kCGWindowListOptionOnScreenOnly = 1 shl 0
private const val kCGWindowListOptionIncludingWindow = 1 shl 3
private const val kCGWindowListExcludeDesktopElements = 1 shl 4
private const val kCGWindowImageBoundsIgnoreFraming = 1 shl 0

/* CGBitmapInfo: byte order + alpha handling. Little-endian, alpha in the (ignored) high byte. */
private const val kCGImageAlphaNoneSkipFirst = 6
private const val kCGBitmapByteOrder32Little = 2 shl 12

/* CFNumberType */
private const val kCFNumberSInt32Type = 3

/* CFStringRef constants exported by CoreGraphics, used as CFDictionary keys. */
private val kCGWindowNumber: Pointer = coreGraphicsString("kCGWindowNumber")
private val kCGWindowOwnerPID: Pointer = coreGraphicsString("kCGWindowOwnerPID")
private val kCGWindowBounds: Pointer = coreGraphicsString("kCGWindowBounds")

private fun coreGraphicsString(name: String): Pointer =
    com.sun.jna.NativeLibrary.getInstance("CoreGraphics").getGlobalVariableAddress(name).getPointer(0)

@Suppress("FunctionName")
private interface CoreGraphics : Library {
    fun CGWindowListCopyWindowInfo(option: Int, relativeToWindow: Int): Pointer?
    fun CGWindowListCreateImage(screenBounds: CGRect.ByValue, listOption: Int, windowId: Int, imageOption: Int): Pointer?
    fun CGRectMakeWithDictionaryRepresentation(dict: Pointer, rect: CGRect): Byte

    fun CGImageGetWidth(image: Pointer): NativeLong
    fun CGImageGetHeight(image: Pointer): NativeLong
    fun CGImageRelease(image: Pointer)

    fun CGColorSpaceCreateDeviceRGB(): Pointer
    fun CGColorSpaceRelease(space: Pointer)

    fun CGBitmapContextCreate(
        data: Pointer, width: NativeLong, height: NativeLong, bitsPerComponent: NativeLong,
        bytesPerRow: NativeLong, space: Pointer, bitmapInfo: Int
    ): Pointer?

    fun CGContextDrawImage(context: Pointer, rect: CGRect.ByValue, image: Pointer)
    fun CGContextRelease(context: Pointer)

    companion object {
        val INSTANCE: CoreGraphics = Native.load("CoreGraphics", CoreGraphics::class.java)
    }
}

@Suppress("FunctionName")
private interface CoreFoundation : Library {
    fun CFArrayGetCount(array: Pointer): NativeLong
    fun CFArrayGetValueAtIndex(array: Pointer, index: NativeLong): Pointer?
    fun CFDictionaryGetValue(dict: Pointer, key: Pointer): Pointer?
    fun CFNumberGetValue(number: Pointer, type: Int, value: Pointer): Byte
    fun CFRelease(ref: Pointer)

    companion object {
        val INSTANCE: CoreFoundation = Native.load("CoreFoundation", CoreFoundation::class.java)
    }
}

@Structure.FieldOrder("x", "y")
internal open class CGPoint : Structure() {
    @JvmField var x: Double = 0.0
    @JvmField var y: Double = 0.0
}

@Structure.FieldOrder("width", "height")
internal open class CGSize : Structure() {
    @JvmField var width: Double = 0.0
    @JvmField var height: Double = 0.0
}

@Structure.FieldOrder("origin", "size")
internal open class CGRect : Structure() {
    @JvmField var origin: CGPoint = CGPoint()
    @JvmField var size: CGSize = CGSize()

    class ByValue() : CGRect(), Structure.ByValue {
        constructor(x: Double, y: Double, width: Double, height: Double) : this() {
            origin.x = x
            origin.y = y
            size.width = width
            size.height = height
        }
    }
}
