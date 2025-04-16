/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.core

import java.util.zip.CRC32

@OptIn(ExperimentalStdlibApi::class)
public fun String.asFileName(): String {
    var result = this
    result = result.replace(Regex("""\\W+"""), "_")

    if (result.length > 200) {
        val crc = CRC32()
        crc.update(result.toByteArray())
        val crcHex = crc.value.toInt().toHexString()

        result = result.take(100) + "..." + result.takeLast(100) + "-$crcHex"
    }

    return result
}
