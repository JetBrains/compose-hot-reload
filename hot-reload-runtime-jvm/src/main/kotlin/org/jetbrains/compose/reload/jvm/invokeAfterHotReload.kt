/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

@file:JvmName("JvmInvokeAfterHotReload")

package org.jetbrains.compose.reload.jvm

private val noopAutoCloseable = AutoCloseable { }

public fun invokeAfterHotReload(@Suppress("unused") block: () -> Unit): AutoCloseable {
    return noopAutoCloseable
}
