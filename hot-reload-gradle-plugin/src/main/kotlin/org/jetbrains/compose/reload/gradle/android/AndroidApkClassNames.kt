/*
 * Copyright 2024-2026 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.compose.reload.gradle.android

import java.nio.file.Path
import java.util.zip.ZipFile

/**
 * The class names inside an APK (its 'classes*.dex' entries) — the names the installed app
 * actually has. The reload task validates its payload against them: the device can only
 * *redefine* classes, so a shipped name absent from the APK could not be applied there.
 */
internal object AndroidApkClassNames {

    /** The class names of all 'classes*.dex' entries of the given APKs. */
    fun of(apks: List<Path>): Set<String> = apks.flatMapTo(mutableSetOf(), ::ofApk)

    private fun ofApk(apk: Path): Set<String> = ZipFile(apk.toFile()).use { zip ->
        zip.entries().asSequence()
            .filter { entry -> !entry.isDirectory && entry.name.startsWith("classes") && entry.name.endsWith(".dex") }
            .flatMap { entry -> ofDex(zip.getInputStream(entry).readBytes()).asSequence() }
            .toSet()
    }

    private fun malformed(detail: String): Nothing = error("Malformed DEX: $detail")

    /**
     * The class names of one DEX.
     *
     * [DEX spec]: https://source.android.com/docs/core/runtime/dex-format
     */
    internal fun ofDex(dex: ByteArray): Set<String> {
        /* Offsets of the tables we walk. */
        val STRING_IDS_SIZE = 0x38
        val STRING_IDS_OFF = 0x3C
        val TYPE_IDS_SIZE = 0x40
        val TYPE_IDS_OFF = 0x44
        val CLASS_DEFS_SIZE = 0x60
        val CLASS_DEFS_OFF = 0x64

        fun u4(offset: Int): Int {
            if (offset < 0 || offset + 4 > dex.size) malformed("4-byte read at $offset is out of bounds")
            return (dex[offset].toInt() and 0xFF) or
                (dex[offset + 1].toInt() and 0xFF shl 8) or
                (dex[offset + 2].toInt() and 0xFF shl 16) or
                (dex[offset + 3].toInt() and 0xFF shl 24)
        }

        fun uTable(name: String, sizeOffset: Int, offOffset: Int, entrySize: Int): Pair<Int, Int> {
            val entries = u4(sizeOffset)
            val off = u4(offOffset)
            if (off.toLong() + entries.toLong() * entrySize > dex.size) {
                malformed("'$name' table ($entries entries at 0x${off.toString(16)}) is out of bounds")
            }
            return entries to off
        }

        if (dex.size < 0x70) malformed("too small for a DEX header (${dex.size} bytes)")

        /* The DEX magic starts with "dex\n" (the version digits follow); little-endian u4. */
        if (u4(0) != 0x0A786564) malformed("bad magic (expected 'dex\\n…')")

        val stringIds = uTable("string_ids", STRING_IDS_SIZE, STRING_IDS_OFF, entrySize = 4)
        val typeIds = uTable("type_ids", TYPE_IDS_SIZE, TYPE_IDS_OFF, entrySize = 4)
        val classDefs = uTable("class_defs", CLASS_DEFS_SIZE, CLASS_DEFS_OFF, entrySize = 32)

        fun stringAt(index: Int): String {
            val dataOffset = u4(stringIds.second + index * 4)
            if (dataOffset < 0 || dataOffset >= dex.size) {
                malformed("string_id $index points outside the file (0x${dataOffset.toString(16)})")
            }
            // skip the uleb128 utf16 length
            var offset = dataOffset
            while (dex[offset].toInt() and 0x80 != 0) {
                if (++offset >= dex.size) malformed("uleb128 of string_id $index runs past the file end")
            }
            var end = ++offset
            while (end < dex.size && dex[end] != 0.toByte()) end++
            if (end >= dex.size) malformed("string_id $index has no NUL terminator")
            return decodeMutf8(dex, offset, end)
        }

        fun typeDescriptor(index: Int): String {
            if (index >= typeIds.first) malformed("type index $index is out of range (${typeIds.first} types)")
            val descriptorIdx = u4(typeIds.second + index * 4)
            if (descriptorIdx >= stringIds.first) {
                malformed("type $index references string $descriptorIdx, out of range (${stringIds.first} strings)")
            }
            return stringAt(descriptorIdx)
        }

        return buildSet {
            for (i in 0 until classDefs.first) {
                val typeIdx = u4(classDefs.second + i * 32)
                val descriptor = typeDescriptor(typeIdx)
                if (descriptor.startsWith("L") && descriptor.endsWith(";")) {
                    add(descriptor.substring(1, descriptor.length - 1).replace('/', '.'))
                }
            }
        }
    }

    /**
     * Decodes MUTF-8.
     *
     * [DEX spec]: https://source.android.com/docs/core/runtime/dex-format#mutf-8
     */
    private fun decodeMutf8(bytes: ByteArray, start: Int, end: Int): String = buildString(end - start) {
        var i = start

        fun next(label: String): Int {
            if (i >= end) malformed("truncated MUTF-8 sequence ('$label' at $i)")
            return bytes[i++].toInt() and 0x3F
        }

        while (i < end) {
            val b0 = bytes[i++].toInt() and 0xFF
            when {
                b0 < 0x80 -> append(b0.toChar())
                b0 shr 5 == 0b110 -> append((((b0 and 0x1F) shl 6) or next("2-byte")).toChar())
                b0 shr 4 == 0b1110 -> append((((b0 and 0x0F) shl 12) or (next("3-byte") shl 6) or next("3-byte")).toChar())
                else -> malformed("invalid MUTF-8 lead byte 0x${b0.toString(16)} at ${i - 1}")
            }
        }
    }
}
