/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.analysis

internal fun isIgnoredClassId(classId: String): Boolean = with(classId) {
    startsWith("java/") ||
        startsWith("javax/") ||
        startsWith("java/") ||
        startsWith("jdk/") ||
        startsWith("com/sun/") ||
        startsWith("io/netty/") ||
        startsWith("sun/") ||
        startsWith("kotlin/") ||
        startsWith("kotlinx/") ||
        startsWith("androidx/") ||
        startsWith("org/jetbrains/skia/") ||
        startsWith("org/jetbrains/skiko/") ||
        startsWith("org/objectweb")
}
