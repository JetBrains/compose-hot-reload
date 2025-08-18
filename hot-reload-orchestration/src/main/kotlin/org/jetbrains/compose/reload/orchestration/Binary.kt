/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.orchestration

import java.io.Serializable

internal class Binary(val bytes: ByteArray) : Serializable {
    val hashCode: Int = bytes.contentHashCode()

    override fun hashCode(): Int {
        return hashCode
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Binary) return false
        if (other.hashCode != hashCode) return false
        return bytes.contentEquals(other.bytes)
    }

    companion object {
        const val serialVersionUID: Long = 0L
    }
}
