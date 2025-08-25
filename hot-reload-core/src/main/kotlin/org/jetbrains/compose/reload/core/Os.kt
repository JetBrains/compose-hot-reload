/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.core

import org.jetbrains.compose.reload.InternalHotReloadApi

@InternalHotReloadApi
public enum class Os {
    Windows, MacOs, Linux;

    @InternalHotReloadApi
    public companion object {
        @JvmStatic
        public fun currentOrNull(): Os? {
            val os = System.getProperty("os.name")
            return when {
                os.startsWith("Mac", ignoreCase = true) -> MacOs
                os.startsWith("Win", ignoreCase = true) -> Windows
                os.startsWith("Linux", ignoreCase = true) -> Linux
                else -> null
            }
        }

        @JvmStatic
        public fun current(): Os = currentOrNull()
            ?: error("Could not determine current OS: ${System.getProperty("os.name")}")
    }
}
