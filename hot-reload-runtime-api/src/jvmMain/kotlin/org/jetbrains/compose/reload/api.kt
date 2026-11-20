/*
 * Copyright 2024-2026 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

@file:JvmName("HotReloadApi")
@file:JvmMultifileClass

package org.jetbrains.compose.reload

import androidx.compose.runtime.Composable

/**
 * Provides an "entry point" for Compose Hot Reload to reload code.
 * Note: When using a regular 'Window', there is *no need anymore* to wrap the code manually.
 * This function might only be applicable for non-window-based applications.
 */
@Composable
@DelicateHotReloadApi
public actual fun DevelopmentEntryPoint(child: @Composable () -> Unit) {
    if (!isHotReloadActive) {
        child()
        return
    }

    /* Forward the call to the runtime-jvm module */
    org.jetbrains.compose.reload.jvm.DevelopmentEntryPoint(child = child)
}
