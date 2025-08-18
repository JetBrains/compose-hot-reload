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
public fun encodeByteArray(encode: DataOutputStream.() -> Unit): ByteArray {
    val baos = ByteArrayOutputStream()
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
