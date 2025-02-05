/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

@file:JvmName("JvmDevelopmentEntryPoint")

package org.jetbrains.compose.reload.jvm

import androidx.compose.runtime.Composable

@Composable
public fun DevelopmentEntryPoint(child: @Composable () -> Unit) {
    child()
}
