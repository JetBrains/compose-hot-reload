/*
 * Copyright 2024-2026 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.android

import android.util.Log

/** Puts Compose into hot-reload mode on [loader] (`Recomposer.setHotReloadEnabled(true)`). */
internal fun enableComposeHotReloadMode(loader: ClassLoader) {
    try {
        org.jetbrains.compose.reload.shared.enableComposeHotReloadMode(loader)
        Log.i(LOG_TAG, "Compose hot reload mode enabled")
    } catch (e: ReflectiveOperationException) {
        Log.w(LOG_TAG, "Failed to enable compose hot reload mode", e)
    }
}

/**
 * Fine-grained invalidation: invalidates only the Compose groups matching [key].
 * [key] is the Compose-compiler group key (e.g. produced by `hot-reload-analysis`).
 */
internal fun invalidateGroupsWithKey(loader: ClassLoader, key: Int) {
    try {
        org.jetbrains.compose.reload.shared.invalidateGroupsWithKey(loader, key)
    } catch (e: ReflectiveOperationException) {
        Log.w(LOG_TAG, "Failed to invalidate groups with key $key", e)
    }
}

/**
 * "Hard" reload: disposes every running recomposer's compositions and recomposes them from
 * scratch. Unlike [invalidateGroupsWithKey] this also resets all error states.
 */
internal fun reloadCompositionState(loader: ClassLoader) {
    org.jetbrains.compose.reload.shared.reloadCompositionState(loader) { message, cause ->
        Log.w(LOG_TAG, message, cause)
    }
}
