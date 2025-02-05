/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.core

import java.io.Serializable
import java.util.UUID

@JvmInline
public value class WindowId(public val value: String) : Serializable {
    public companion object {
        public fun create(): WindowId = WindowId(UUID.randomUUID().toString())
    }

    override fun toString(): String {
        return "WindowId(value='$value')"
    }
}
