/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.analysis

import java.nio.ByteBuffer
import java.util.Collections.singletonMap
import java.util.zip.Checksum

internal fun Checksum.updateBoolean(value: Boolean) {
    update(if (value) 1 else 0)
}

internal fun Checksum.updateInt(value: Int) {
    val buffer = ByteBuffer.allocate(4).putInt(value).array()
    update(buffer)
}

internal fun Checksum.updateFloat(value: Float) {
    val buffer = ByteBuffer.allocate(4).putFloat(value).array()
    update(buffer)
}

internal fun Checksum.updateLong(value: Long) {
    val buffer = ByteBuffer.allocate(8).putLong(value).array()
    update(buffer)
}

internal fun Checksum.updateDouble(value: Double) {
    val buffer = ByteBuffer.allocate(8).putDouble(value).array()
    update(buffer)
}

internal fun Checksum.updateString(value: String) {
    updateInt(value.length)
    update(value.encodeToByteArray())
}

internal fun <K, V> HashMap<K, V>.toReadOnlyHashMap(): Map<K, V> = when (size) {
    0 -> emptyMap()
    1 -> entries.first().let { singletonMap(it.key, it.value) }
    else -> this
}

internal fun <K, V> Map<K, V>.toHashMap(): HashMap<K, V> = HashMap(this)