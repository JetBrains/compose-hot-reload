/*
 * Copyright 2024-2026 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.jvm

import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.ptr.IntByReference
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.info
import org.jetbrains.compose.reload.core.warn
import java.awt.Window
import java.awt.image.BufferedImage
import kotlin.math.abs

private val logger = createLogger()

/**
 * Captures a window on Windows using GDI's `PrintWindow`, even when the window is obscured by other
 * windows.
 *
 * [java.awt.Robot.createScreenCapture] reads pixels off the actual screen, so anything covering the
 * window ends up in the screenshot. `PrintWindow` instead asks the window to render itself into a
 * device context, which works regardless of what is in front of it. The `PW_RENDERFULLCONTENT` flag
 * is required for GPU/DirectComposition rendered windows (such as Skia/Compose) — without it those
 * render as a black image.
 *
 * The flow is:
 *  1. Enumerate top-level windows ([User32.EnumWindows]) and find the visible one that belongs to
 *     this process (matching pid) and whose bounds best match the AWT [window].
 *  2. Render that window into a memory bitmap ([User32.PrintWindow]).
 *  3. Read the bitmap pixels ([GDI32.GetDIBits]) into a [BufferedImage].
 *
 * Returns `null` (after logging a reason) if anything goes wrong, so the caller can fall back to the
 * [java.awt.Robot] based capture.
 */
internal fun captureWindowWindows(window: Window): BufferedImage? {
    return try {
        val hwnd = findWindowHandle(window)
        if (hwnd == null) {
            logger.warn("Windows screenshot: could not find a native window matching the AWT window; falling back")
            return null
        }

        captureWindowImage(hwnd)
    } catch (t: Throwable) {
        logger.warn("Windows screenshot: native capture failed; falling back", t)
        null
    }
}

private class WindowCandidate(val hwnd: Pointer, val x: Int, val y: Int, val width: Int, val height: Int)

/**
 * Finds the native window handle for the given AWT [window] by enumerating the visible top-level
 * windows owned by this process and matching their bounds against the AWT window's bounds.
 */
private fun findWindowHandle(window: Window): Pointer? {
    val pid = ProcessHandle.current().pid().toInt()

    /*
     * AWT reports bounds in logical (DPI-unscaled) coordinates, while GetWindowRect returns physical
     * pixels. Scale the AWT bounds by the device transform so the two are comparable on HiDPI screens.
     */
    val scale = window.graphicsConfiguration?.defaultTransform?.scaleX ?: 1.0
    val location = window.locationOnScreen
    val targetX = location.x * scale
    val targetY = location.y * scale
    val targetWidth = window.width * scale
    val targetHeight = window.height * scale

    val candidates = ArrayList<WindowCandidate>()

    /* Keep a strong reference to the callback for the duration of the (synchronous) EnumWindows call. */
    val collector = object : WndEnumProc {
        override fun callback(hWnd: Pointer?, lParam: Pointer?): Boolean {
            if (hWnd == null) return true

            val windowPid = IntByReference()
            User32.INSTANCE.GetWindowThreadProcessId(hWnd, windowPid)
            if (windowPid.value != pid) return true
            if (!User32.INSTANCE.IsWindowVisible(hWnd)) return true

            val rect = RECT()
            if (!User32.INSTANCE.GetWindowRect(hWnd, rect)) return true
            val width = rect.right - rect.left
            val height = rect.bottom - rect.top
            if (width <= 0 || height <= 0) return true

            candidates.add(WindowCandidate(hWnd, rect.left, rect.top, width, height))
            return true
        }
    }
    User32.INSTANCE.EnumWindows(collector, null)

    val best = candidates.minByOrNull { candidate ->
        abs(candidate.x - targetX) + abs(candidate.y - targetY) +
            abs(candidate.width - targetWidth) + abs(candidate.height - targetHeight)
    }

    logger.info(
        "Windows screenshot: pid=$pid, scale=$scale, candidates(visible, pid match)=${candidates.size}, " +
            "best=${best?.let { "[${it.x},${it.y} ${it.width}x${it.height}]" }}, " +
            "awtBounds(scaled)=[${targetX.toInt()},${targetY.toInt()} ${targetWidth.toInt()}x${targetHeight.toInt()}]"
    )

    return best?.hwnd
}

/**
 * Renders the window with the given [hwnd] into a memory bitmap and converts it into a [BufferedImage].
 */
private fun captureWindowImage(hwnd: Pointer): BufferedImage? {
    val rect = RECT()
    if (!User32.INSTANCE.GetWindowRect(hwnd, rect)) {
        logger.warn("Windows screenshot: GetWindowRect failed")
        return null
    }
    val width = rect.right - rect.left
    val height = rect.bottom - rect.top
    if (width <= 0 || height <= 0) {
        logger.warn("Windows screenshot: window has invalid size ${width}x$height")
        return null
    }

    val windowDc = User32.INSTANCE.GetWindowDC(hwnd) ?: run {
        logger.warn("Windows screenshot: GetWindowDC returned null")
        return null
    }

    try {
        val memDc = GDI32.INSTANCE.CreateCompatibleDC(windowDc) ?: run {
            logger.warn("Windows screenshot: CreateCompatibleDC returned null")
            return null
        }
        try {
            val bitmap = GDI32.INSTANCE.CreateCompatibleBitmap(windowDc, width, height) ?: run {
                logger.warn("Windows screenshot: CreateCompatibleBitmap returned null")
                return null
            }
            try {
                val previous = GDI32.INSTANCE.SelectObject(memDc, bitmap)
                val printed = User32.INSTANCE.PrintWindow(hwnd, memDc, PW_RENDERFULLCONTENT)
                /* GetDIBits requires the bitmap not to be selected into a DC. */
                if (previous != null) GDI32.INSTANCE.SelectObject(memDc, previous)

                if (!printed) {
                    logger.warn("Windows screenshot: PrintWindow returned false (capturing result anyway)")
                }

                val info = BITMAPINFO()
                info.bmiHeader.biSize = info.bmiHeader.size()
                info.bmiHeader.biWidth = width
                info.bmiHeader.biHeight = -height // negative => top-down rows
                info.bmiHeader.biPlanes = 1
                info.bmiHeader.biBitCount = 32
                info.bmiHeader.biCompression = BI_RGB

                val buffer = Memory(width.toLong() * height * 4)
                val scanLines = GDI32.INSTANCE.GetDIBits(windowDc, bitmap, 0, height, buffer, info, DIB_RGB_COLORS)
                if (scanLines == 0) {
                    logger.warn("Windows screenshot: GetDIBits returned 0 scan lines")
                    return null
                }

                /*
                 * The bitmap is 32-bit BI_RGB, i.e. the bytes in memory are B,G,R,X. Read as native
                 * (little-endian) ints, each pixel becomes 0xXXRRGGBB, which is exactly the layout that
                 * BufferedImage.TYPE_INT_RGB expects (the high byte is ignored).
                 */
                val pixels = buffer.getIntArray(0, width * height)
                val result = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
                result.setRGB(0, 0, width, height, pixels, 0, width)
                logger.info("Windows screenshot: captured window as ${width}x$height image")
                return result
            } finally {
                GDI32.INSTANCE.DeleteObject(bitmap)
            }
        } finally {
            GDI32.INSTANCE.DeleteDC(memDc)
        }
    } finally {
        User32.INSTANCE.ReleaseDC(hwnd, windowDc)
    }
}

/* PrintWindow flag: render the full window content, including DirectComposition/GPU surfaces. */
private const val PW_RENDERFULLCONTENT = 0x00000002

private const val BI_RGB = 0
private const val DIB_RGB_COLORS = 0

private interface WndEnumProc : Callback {
    fun callback(hWnd: Pointer?, lParam: Pointer?): Boolean
}

@Suppress("FunctionName")
private interface User32 : Library {
    fun EnumWindows(callback: WndEnumProc, lParam: Pointer?): Boolean
    fun GetWindowThreadProcessId(hWnd: Pointer, lpdwProcessId: IntByReference): Int
    fun IsWindowVisible(hWnd: Pointer): Boolean
    fun GetWindowRect(hWnd: Pointer, rect: RECT): Boolean
    fun GetWindowDC(hWnd: Pointer): Pointer?
    fun ReleaseDC(hWnd: Pointer, hDC: Pointer): Int
    fun PrintWindow(hWnd: Pointer, hdcBlt: Pointer, nFlags: Int): Boolean

    companion object {
        val INSTANCE: User32 = Native.load("user32", User32::class.java)
    }
}

@Suppress("FunctionName")
private interface GDI32 : Library {
    fun CreateCompatibleDC(hdc: Pointer): Pointer?
    fun CreateCompatibleBitmap(hdc: Pointer, width: Int, height: Int): Pointer?
    fun SelectObject(hdc: Pointer, hgdiobj: Pointer): Pointer?
    fun DeleteObject(hObject: Pointer): Boolean
    fun DeleteDC(hdc: Pointer): Boolean
    fun GetDIBits(
        hdc: Pointer, hbmp: Pointer, uStartScan: Int, cScanLines: Int,
        lpvBits: Pointer, lpbi: BITMAPINFO, uUsage: Int
    ): Int

    companion object {
        val INSTANCE: GDI32 = Native.load("gdi32", GDI32::class.java)
    }
}

@Structure.FieldOrder("left", "top", "right", "bottom")
internal open class RECT : Structure() {
    @JvmField var left: Int = 0
    @JvmField var top: Int = 0
    @JvmField var right: Int = 0
    @JvmField var bottom: Int = 0
}

@Structure.FieldOrder(
    "biSize", "biWidth", "biHeight", "biPlanes", "biBitCount", "biCompression",
    "biSizeImage", "biXPelsPerMeter", "biYPelsPerMeter", "biClrUsed", "biClrImportant"
)
internal open class BITMAPINFOHEADER : Structure() {
    @JvmField var biSize: Int = 0
    @JvmField var biWidth: Int = 0
    @JvmField var biHeight: Int = 0
    @JvmField var biPlanes: Short = 0
    @JvmField var biBitCount: Short = 0
    @JvmField var biCompression: Int = 0
    @JvmField var biSizeImage: Int = 0
    @JvmField var biXPelsPerMeter: Int = 0
    @JvmField var biYPelsPerMeter: Int = 0
    @JvmField var biClrUsed: Int = 0
    @JvmField var biClrImportant: Int = 0
}

@Structure.FieldOrder("bmiHeader", "bmiColors")
internal open class BITMAPINFO : Structure() {
    @JvmField var bmiHeader: BITMAPINFOHEADER = BITMAPINFOHEADER()
    @JvmField var bmiColors: IntArray = IntArray(1)
}
