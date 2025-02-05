/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

@file:JvmName("isHotReloadActive")

package org.jetbrains.compose.reload.jvm


@Suppress("MayBeConstant") // Should not be a constant as the other jar will provide a different value!
public val isHotReloadActive: Boolean = false
