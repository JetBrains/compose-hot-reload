/*
 * Copyright 2024-2026 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.jvm

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.NativeLong
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.NativeLongByReference
import com.sun.jna.ptr.PointerByReference
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.info
import org.jetbrains.compose.reload.core.warn
import java.awt.Window
import java.awt.image.BufferedImage
import kotlin.math.abs

private val logger = createLogger()

/**
 * Captures a window on Linux/X11 using Xlib, even when the window is obscured by other windows.
 *
 * [java.awt.Robot.createScreenCapture] reads pixels off the actual screen, so anything covering the
 * window ends up in the screenshot. Instead, when a compositing manager is running, every top-level
 * window is redirected to an off-screen pixmap that holds its full contents; we grab that pixmap via
 * `XCompositeNameWindowPixmap` + `XGetImage`, which works regardless of what is in front of it.
 *
 * The flow is:
 *  1. Find the top-level frame window for this AWT [window]: walk the X11 window tree, locate the
 *     window owning `_NET_WM_PID == our pid`, and take its ancestor that is a direct child of the
 *     root (the window-manager decoration frame).
 *  2. Name the redirected off-screen pixmap of that frame ([XComposite.XCompositeNameWindowPixmap]),
 *     falling back to the window itself if no compositor is running.
 *  3. Read the pixels ([X11.XGetImage]) and convert them into a [BufferedImage].
 *
 * Returns `null` (after logging a reason) if anything goes wrong, so the caller can fall back to the
 * [java.awt.Robot] based capture.
 *
 * Notes:
 *  - On Wayland the JVM runs through XWayland, so its windows are X11 windows and this path applies.
 *  - Without a running compositor, the off-screen pixmap is unavailable and obscured regions cannot
 *    be recovered; we still capture the (visible) window content directly.
 */
internal fun captureWindowLinux(window: Window): BufferedImage? {
    return try {
        val display = X11.INSTANCE.XOpenDisplay(null)
        if (display == null) {
            logger.warn("Linux screenshot: XOpenDisplay returned null")
            return null
        }
        try {
            val frame = findFrameWindow(display, window)
            if (frame == null) {
                logger.warn("Linux screenshot: could not find a native window matching the AWT window; falling back")
                return null
            }
            captureDrawable(display, frame)
        } finally {
            X11.INSTANCE.XCloseDisplay(display)
        }
    } catch (t: Throwable) {
        logger.warn("Linux screenshot: native capture failed; falling back", t)
        null
    }
}

/**
 * Finds the top-level (window-manager frame) window for the given AWT [window] by walking the X11
 * window tree, matching `_NET_WM_PID` against this process, and taking the matching window's ancestor
 * that is a direct child of the root window.
 */
private fun findFrameWindow(display: Pointer, window: Window): NativeLong? {
    val pid = ProcessHandle.current().pid().toInt()

    val netWmPid = X11.INSTANCE.XInternAtom(display, "_NET_WM_PID", TRUE)
    if (netWmPid.toLong() == 0L) {
        logger.warn("Linux screenshot: _NET_WM_PID atom not available")
        return null
    }

    /* X11 geometry is in physical pixels; AWT bounds are logical. Scale to compare on HiDPI screens. */
    val scale = window.graphicsConfiguration?.defaultTransform?.scaleX ?: 1.0
    val targetWidth = window.width * scale
    val targetHeight = window.height * scale

    val root = X11.INSTANCE.XDefaultRootWindow(display)
    val topLevels = queryChildren(display, root)

    var bestFrame: NativeLong? = null
    var bestScore = Double.MAX_VALUE
    var candidates = 0

    for (topLevel in topLevels) {
        val clientSize = findClientByPid(display, topLevel, pid, netWmPid, depth = 4) ?: continue
        candidates++
        val score = abs(clientSize.first - targetWidth) + abs(clientSize.second - targetHeight)
        if (score < bestScore) {
            bestScore = score
            bestFrame = topLevel
        }
    }

    logger.info(
        "Linux screenshot: pid=$pid, scale=$scale, topLevels=${topLevels.size}, candidates(pid match)=$candidates, " +
            "bestFrame=${bestFrame?.toLong()}, bestScore=$bestScore, " +
            "awtSize(scaled)=${targetWidth.toInt()}x${targetHeight.toInt()}"
    )

    return bestFrame
}

/**
 * Recursively searches the subtree rooted at [window] (up to [depth] levels) for a window whose
 * `_NET_WM_PID` equals [pid]. Returns the matching window's `(width, height)` if found.
 */
private fun findClientByPid(display: Pointer, window: NativeLong, pid: Int, netWmPid: NativeLong, depth: Int): Pair<Int, Int>? {
    if (readNetWmPid(display, window, netWmPid) == pid) {
        return getWindowSize(display, window)
    }
    if (depth <= 0) return null
    for (child in queryChildren(display, window)) {
        val found = findClientByPid(display, child, pid, netWmPid, depth - 1)
        if (found != null) return found
    }
    return null
}

/** Names the redirected off-screen pixmap of [frame] (if a compositor is running) and grabs it. */
private fun captureDrawable(display: Pointer, frame: NativeLong): BufferedImage? {
    var pixmap = NativeLong(0)
    var drawable = frame

    /* libXcomposite may be absent, or the window may not be redirected (no compositor): tolerate both. */
    try {
        val eventBase = IntByReference()
        val errorBase = IntByReference()
        if (XComposite.INSTANCE.XCompositeQueryExtension(display, eventBase, errorBase) != 0) {
            val named = XComposite.INSTANCE.XCompositeNameWindowPixmap(display, frame)
            if (named.toLong() != 0L) {
                pixmap = named
                drawable = named
            } else {
                logger.warn("Linux screenshot: XCompositeNameWindowPixmap returned 0 (window not redirected?)")
            }
        } else {
            logger.warn("Linux screenshot: Composite extension not available")
        }
    } catch (t: Throwable) {
        logger.warn("Linux screenshot: XComposite unavailable, capturing window directly", t)
    }

    try {
        val size = getWindowSize(display, drawable)
        if (size == null || size.first <= 0 || size.second <= 0) {
            logger.warn("Linux screenshot: could not determine drawable geometry")
            return null
        }
        val (width, height) = size

        var image = X11.INSTANCE.XGetImage(display, drawable, 0, 0, width, height, ALL_PLANES, Z_PIXMAP)
        if (image == null && pixmap.toLong() != 0L) {
            /* The composite pixmap grab failed; retry directly on the window. */
            logger.warn("Linux screenshot: XGetImage on the composite pixmap failed, retrying on the window")
            image = X11.INSTANCE.XGetImage(display, frame, 0, 0, width, height, ALL_PLANES, Z_PIXMAP)
        }
        if (image == null) {
            logger.warn("Linux screenshot: XGetImage returned null")
            return null
        }

        val result = convertXImage(image)
        val method =
            if (pixmap.toLong() != 0L) "via composite pixmap"
            else "directly (no compositor — obscured regions may be blank)"
        logger.info("Linux screenshot: captured window $method as ${result.width}x${result.height} image")
        return result
    } finally {
        if (pixmap.toLong() != 0L) X11.INSTANCE.XFreePixmap(display, pixmap)
    }
}

/** Converts an `XImage*` into a [BufferedImage] and frees the native image. */
private fun convertXImage(imagePtr: Pointer): BufferedImage {
    val image = XImage(imagePtr)
    image.read()

    val width = image.width
    val height = image.height
    val data = image.data ?: error("XImage has no data")
    val bytesPerLine = image.bytes_per_line
    val bitsPerPixel = image.bits_per_pixel
    val redMask = image.red_mask.toLong() and 0xFFFFFFFFL
    val greenMask = image.green_mask.toLong() and 0xFFFFFFFFL
    val blueMask = image.blue_mask.toLong() and 0xFFFFFFFFL

    try {
        val result = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)

        /*
         * Fast path for the near-universal local case: 32 bits per pixel, standard TrueColor masks,
         * no row padding, native (little-endian) byte order. Each pixel reads as 0xXXRRGGBB, which is
         * exactly what TYPE_INT_RGB expects (the high byte is ignored).
         */
        if (bitsPerPixel == 32 && redMask == 0xFF0000L && greenMask == 0xFF00L && blueMask == 0xFFL &&
            bytesPerLine == width * 4
        ) {
            val pixels = data.getIntArray(0, width * height)
            result.setRGB(0, 0, width, height, pixels, 0, width)
            return result
        }

        /* Generic path: extract channels via the image's masks. */
        val redShift = java.lang.Long.numberOfTrailingZeros(redMask)
        val greenShift = java.lang.Long.numberOfTrailingZeros(greenMask)
        val blueShift = java.lang.Long.numberOfTrailingZeros(blueMask)

        for (y in 0 until height) {
            val rowOffset = y.toLong() * bytesPerLine
            for (x in 0 until width) {
                val pixel = when (bitsPerPixel) {
                    32 -> data.getInt(rowOffset + x * 4L).toLong() and 0xFFFFFFFFL
                    24 -> {
                        val base = rowOffset + x * 3L
                        val b0 = data.getByte(base).toLong() and 0xFF
                        val b1 = data.getByte(base + 1).toLong() and 0xFF
                        val b2 = data.getByte(base + 2).toLong() and 0xFF
                        b0 or (b1 shl 8) or (b2 shl 16)
                    }
                    else -> 0L
                }
                val r = ((pixel and redMask) ushr redShift).toInt() and 0xFF
                val g = ((pixel and greenMask) ushr greenShift).toInt() and 0xFF
                val b = ((pixel and blueMask) ushr blueShift).toInt() and 0xFF
                result.setRGB(x, y, (r shl 16) or (g shl 8) or b)
            }
        }
        return result
    } finally {
        /*
         * XGetImage's default destroy_image frees both the pixel buffer and the struct; we do the
         * equivalent manually to avoid calling the function pointer inside the XImage struct.
         */
        X11.INSTANCE.XFree(data)
        X11.INSTANCE.XFree(imagePtr)
    }
}

/** Reads the `_NET_WM_PID` cardinal property of [window], or `null` if it is not set. */
private fun readNetWmPid(display: Pointer, window: NativeLong, netWmPid: NativeLong): Int? {
    val actualType = NativeLongByReference()
    val actualFormat = IntByReference()
    val nitems = NativeLongByReference()
    val bytesAfter = NativeLongByReference()
    val prop = PointerByReference()

    val status = X11.INSTANCE.XGetWindowProperty(
        display, window, netWmPid, NativeLong(0), NativeLong(1), FALSE, XA_CARDINAL,
        actualType, actualFormat, nitems, bytesAfter, prop
    )
    if (status != 0 /* Success */) return null

    val value = prop.value ?: return null
    if (value == Pointer.NULL) return null
    try {
        if (nitems.value.toLong() < 1L) return null
        /* 32-bit format properties are returned as an array of C long (8 bytes on LP64). */
        return value.getNativeLong(0).toInt()
    } finally {
        X11.INSTANCE.XFree(value)
    }
}

/** Returns the `(width, height)` of [drawable], or `null` on failure. */
private fun getWindowSize(display: Pointer, drawable: NativeLong): Pair<Int, Int>? {
    val rootRet = NativeLongByReference()
    val x = IntByReference()
    val y = IntByReference()
    val width = IntByReference()
    val height = IntByReference()
    val border = IntByReference()
    val depth = IntByReference()
    val status = X11.INSTANCE.XGetGeometry(display, drawable, rootRet, x, y, width, height, border, depth)
    if (status == 0) return null
    return width.value to height.value
}

/** Returns the child windows of [window] (XQueryTree), or an empty list on failure. */
private fun queryChildren(display: Pointer, window: NativeLong): List<NativeLong> {
    val rootRet = NativeLongByReference()
    val parentRet = NativeLongByReference()
    val childrenRet = PointerByReference()
    val nChildren = IntByReference()

    if (X11.INSTANCE.XQueryTree(display, window, rootRet, parentRet, childrenRet, nChildren) == 0) {
        return emptyList()
    }

    val childrenPtr = childrenRet.value
    val count = nChildren.value
    return try {
        if (childrenPtr == null || childrenPtr == Pointer.NULL || count <= 0) emptyList()
        else childrenPtr.getLongArray(0, count).map { NativeLong(it) }
    } finally {
        if (childrenPtr != null && childrenPtr != Pointer.NULL) X11.INSTANCE.XFree(childrenPtr)
    }
}

private const val TRUE = 1
private const val FALSE = 0
private const val Z_PIXMAP = 2
private val ALL_PLANES = NativeLong(-1L) // all bits set
private val XA_CARDINAL = NativeLong(6L) // predefined atom

@Suppress("FunctionName")
private interface X11 : Library {
    fun XOpenDisplay(name: String?): Pointer?
    fun XCloseDisplay(display: Pointer): Int
    fun XDefaultRootWindow(display: Pointer): NativeLong
    fun XInternAtom(display: Pointer, name: String, onlyIfExists: Int): NativeLong

    fun XQueryTree(
        display: Pointer, w: NativeLong, rootReturn: NativeLongByReference, parentReturn: NativeLongByReference,
        childrenReturn: PointerByReference, nChildrenReturn: IntByReference
    ): Int

    fun XGetWindowProperty(
        display: Pointer, w: NativeLong, property: NativeLong, longOffset: NativeLong, longLength: NativeLong,
        delete: Int, reqType: NativeLong, actualTypeReturn: NativeLongByReference, actualFormatReturn: IntByReference,
        nitemsReturn: NativeLongByReference, bytesAfterReturn: NativeLongByReference, propReturn: PointerByReference
    ): Int

    fun XGetGeometry(
        display: Pointer, d: NativeLong, rootReturn: NativeLongByReference, xReturn: IntByReference,
        yReturn: IntByReference, widthReturn: IntByReference, heightReturn: IntByReference,
        borderWidthReturn: IntByReference, depthReturn: IntByReference
    ): Int

    fun XGetImage(
        display: Pointer, d: NativeLong, x: Int, y: Int, width: Int, height: Int, planeMask: NativeLong, format: Int
    ): Pointer?

    fun XFreePixmap(display: Pointer, pixmap: NativeLong): Int
    fun XFree(data: Pointer): Int

    companion object {
        val INSTANCE: X11 = Native.load("X11", X11::class.java)
    }
}

@Suppress("FunctionName")
private interface XComposite : Library {
    fun XCompositeQueryExtension(display: Pointer, eventBaseReturn: IntByReference, errorBaseReturn: IntByReference): Int
    fun XCompositeNameWindowPixmap(display: Pointer, window: NativeLong): NativeLong

    companion object {
        val INSTANCE: XComposite = Native.load("Xcomposite", XComposite::class.java)
    }
}

/**
 * Mapping of the leading fields of Xlib's `XImage`. Only the fields needed to read back pixels are
 * declared; the trailing `obdata`/function-table fields are intentionally omitted (we only read this
 * struct from a pointer returned by [X11.XGetImage], never allocate it ourselves).
 */
@Structure.FieldOrder(
    "width", "height", "xoffset", "format", "data", "byte_order", "bitmap_unit", "bitmap_bit_order",
    "bitmap_pad", "depth", "bytes_per_line", "bits_per_pixel", "red_mask", "green_mask", "blue_mask"
)
internal open class XImage(pointer: Pointer) : Structure(pointer) {
    @JvmField var width: Int = 0
    @JvmField var height: Int = 0
    @JvmField var xoffset: Int = 0
    @JvmField var format: Int = 0
    @JvmField var data: Pointer? = null
    @JvmField var byte_order: Int = 0
    @JvmField var bitmap_unit: Int = 0
    @JvmField var bitmap_bit_order: Int = 0
    @JvmField var bitmap_pad: Int = 0
    @JvmField var depth: Int = 0
    @JvmField var bytes_per_line: Int = 0
    @JvmField var bits_per_pixel: Int = 0
    @JvmField var red_mask: NativeLong = NativeLong(0)
    @JvmField var green_mask: NativeLong = NativeLong(0)
    @JvmField var blue_mask: NativeLong = NativeLong(0)
}
