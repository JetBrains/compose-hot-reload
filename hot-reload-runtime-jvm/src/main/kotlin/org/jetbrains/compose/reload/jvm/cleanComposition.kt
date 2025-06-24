/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.jvm

import kotlinx.coroutines.flow.update

internal fun cleanComposition() {
    hotReloadState.update { state -> state.copy(key = state.key + 1) }
    retryFailedCompositions()
}
