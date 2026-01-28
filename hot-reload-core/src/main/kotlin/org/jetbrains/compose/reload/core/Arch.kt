/*
 * Copyright 2024-2026 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.core

import org.jetbrains.compose.reload.InternalHotReloadApi


@InternalHotReloadApi
public enum class Arch(public val value: String) {
    X64("x64"),
    AARCH64("aarch64");

    @InternalHotReloadApi
    public companion object {
        @JvmStatic
        public fun currentOrNull(): Arch? = when (System.getProperty("os.arch")) {
            "x86_64", "amd64" -> X64
            "aarch64" -> AARCH64
            else -> null
        }

        @JvmStatic
        public fun current(): Arch = currentOrNull()
            ?: error("Could not determine current arch: ${System.getProperty("os.arch")}")
    }
}