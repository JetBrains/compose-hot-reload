/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.test

internal fun lowerCamelCase(vararg parts: String?): String {
    return buildString {
        parts.filterNotNull().filter { it.isNotBlank() }.forEachIndexed { i, part ->
            if (i > 0) append(part.replaceFirstChar { it.uppercaseChar() })
            else append(part)
        }
    }
}
