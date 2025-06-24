/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

@file:JvmName("HotReloadApi")

package org.jetbrains.compose.reload

import androidx.compose.runtime.Composable
import kotlin.jvm.JvmName

@Composable
public actual fun DevelopmentEntryPoint(child: @Composable () -> Unit) {
    child()
}

public actual val staticHotReloadScope: HotReloadScope = NoopHotReloadScope

@Composable
public actual fun AfterHotReloadEffect(action: () -> Unit): Unit = Unit

public actual val isHotReloadActive: Boolean = false
