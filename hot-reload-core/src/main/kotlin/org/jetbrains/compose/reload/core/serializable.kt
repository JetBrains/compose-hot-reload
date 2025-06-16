/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.core

import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable

public fun Serializable.encodeSerializableObject(): ByteArray {
    val baos = ByteArrayOutputStream()
    ObjectOutputStream(baos).use { stream ->
        stream.writeObject(this)
    }
    return baos.toByteArray()
}

public fun ByteArray.decodeSerializableObject(): Any? {
    return ObjectInputStream(inputStream()).use { stream ->
        stream.readObject()
    }
}
