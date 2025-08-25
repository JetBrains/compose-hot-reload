/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.core

import org.jetbrains.compose.reload.InternalHotReloadApi
import java.io.File
import java.util.zip.CRC32

@InternalHotReloadApi
@OptIn(ExperimentalStdlibApi::class)
public fun String.asFileName(): String {
    var result = this
    val chars = result.toCharArray()
    chars.forEachIndexed { index, c ->
        chars[index] = when {
            c.isLetterOrDigit() -> c
            c == '.' -> c
            c == '-' -> c
            c == ',' -> c
            c == ';' -> c
            c == '(' -> c
            c == ')' -> c
            c == ' ' -> c
            c == '#' -> c
            else -> '_'
        }
    }

    result = chars.concatToString()

    if (result.length > 200) {
        val crc = CRC32()
        crc.update(result.toByteArray())
        val crcHex = crc.value.toInt().toHexString()

        result = result.take(100) + "..." + result.takeLast(100) + "-$crcHex"
    }

    return result
}

@InternalHotReloadApi
public fun File.isClass(): Boolean = extension == "class"
