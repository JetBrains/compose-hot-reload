/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.gradle

import org.jetbrains.compose.reload.InternalHotReloadApi

@RequiresOptIn(
    "This API is internal to hot-reload-gradle-plugin and should not be used from outside.",
    level = RequiresOptIn.Level.ERROR
)
@Deprecated(level = DeprecationLevel.WARNING, message = "Use 'InternalHotReloadApi' instead.")
@OptIn(InternalHotReloadApi::class)
annotation class InternalHotReloadGradleApi()
