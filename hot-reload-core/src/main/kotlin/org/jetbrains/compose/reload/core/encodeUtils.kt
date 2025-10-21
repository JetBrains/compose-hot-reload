/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.core

import org.jetbrains.compose.reload.InternalHotReloadApi
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

@InternalHotReloadApi
public fun encodeByteArray(initialSize: Int = 32, encode: DataOutputStream.() -> Unit): ByteArray {
    val baos = ByteArrayOutputStream(initialSize)
    val daos = DataOutputStream(baos)
    daos.encode()
    return baos.toByteArray()
}

@InternalHotReloadApi
public fun <T> ByteArray.decode(decode: DataInputStream.() -> T): T {
    return DataInputStream(ByteArrayInputStream(this)).decode()
}

@InternalHotReloadApi
public fun <T> ByteArray.tryDecode(decode: DataInputStream.() -> T): Try<T> = Try {
    decode(decode)
}

@InternalHotReloadApi
public fun DataOutputStream.writeString(value: String) {
    writeFrame(value.encodeToByteArray())
}

@InternalHotReloadApi
public fun DataInputStream.readString(): String {
    return readFrame().decodeToString()
}

@InternalHotReloadApi
public fun DataOutputStream.writeFrame(bytes: ByteArray) {
    writeInt(bytes.size)
    write(bytes)
}

@InternalHotReloadApi
public fun DataInputStream.readFrame(): ByteArray {
    val size = readInt()
    return readNBytes(size)
}

@InternalHotReloadApi
public fun DataOutputStream.writeOptionalFrame(bytes: ByteArray?) {
    if (bytes == null) {
        writeInt(-1)
        return
    }

    writeFrame(bytes)
}

@InternalHotReloadApi
public fun DataInputStream.readOptionalFrame(): ByteArray? {
    val size = readInt()
    if (size < 0) return null
    return readNBytes(size)
}

@InternalHotReloadApi
public fun DataOutputStream.writeField(name: String, value: ByteArray?) {
    writeString(name)
    writeOptionalFrame(value)
}

@InternalHotReloadApi
public fun DataInputStream.readField(): Pair<String, ByteArray?> {
    return readString() to readOptionalFrame()
}


@InternalHotReloadApi
public fun DataOutputStream.writeFields(fields: Map<String, ByteArray?>) {
    writeInt(fields.size)
    for ((key, value) in fields) {
        writeField(key, value)
    }
}

@InternalHotReloadApi
public fun DataOutputStream.writeFields(vararg fields: Pair<String, ByteArray?>) {
    writeFields(fields.toMap())
}


@InternalHotReloadApi
public fun Boolean.encodeToByteArray(): ByteArray {
    return if (this) return byteArrayOf(1) else byteArrayOf(0)
}

@OptIn(ExperimentalStdlibApi::class)
@InternalHotReloadApi
public fun ByteArray.decodeToBoolean(): Boolean {
    require(this.size == 1) { "Invalid boolean value: ${this.toHexString()}" }
    return this[0] != 0.toByte()
}

@InternalHotReloadApi
public fun Long.encodeToByteArray(): ByteArray {
    return encodeByteArray(8) { writeLong(this@encodeToByteArray) }
}

@OptIn(ExperimentalStdlibApi::class)
@InternalHotReloadApi
public fun ByteArray.decodeToLong(): Long {
    require(this.size == 8) { "Invalid long value: ${this.toHexString()}" }
    return decode { readLong() }
}

@InternalHotReloadApi
public fun Int.encodeToByteArray(): ByteArray {
    return encodeByteArray(4) { writeInt(this@encodeToByteArray) }
}

@InternalHotReloadApi
@OptIn(ExperimentalStdlibApi::class)
public fun ByteArray.decodeToInt(): Int {
    require(this.size == 4) { "Invalid int value: ${this.toHexString()}" }
    return decode { readInt() }
}


@InternalHotReloadApi
public fun DataInputStream.readFields(): Map<String, ByteArray?> {
    val size = readInt()
    return buildMap {
        repeat(size) {
            val (name, bytes) = readField()
            put(name, bytes)
        }
    }
}

@InternalHotReloadApi
public fun Map<String, ByteArray?>.requireField(key: String): ByteArray {
    if (key !in this) error("Missing field '$key'")
    return get(key) ?: error("Field '$key' is null")
}
