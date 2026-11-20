/*
 * Copyright 2024-2026 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.shared

import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.debug
import org.jetbrains.compose.reload.core.warn
import java.lang.reflect.InvocationTargetException

private val logger = createLogger()

private class RecomposerCompanion(val instance: Any?, val type: Class<*>)

private fun recomposerCompanion(loader: ClassLoader): RecomposerCompanion {
    val recomposerClass = loader.loadClass("androidx.compose.runtime.Recomposer")
    val recomposerCompanion = recomposerClass.getField("Companion").get(null)
    val recomposerCompanionClass = loader.loadClass("androidx.compose.runtime.Recomposer\$Companion")
    return RecomposerCompanion(recomposerCompanion, recomposerCompanionClass)
}

/**
 * Puts Compose into hot-reload mode on [loader] ('Recomposer.setHotReloadEnabled(true)').
 */
fun enableComposeHotReloadMode(loader: ClassLoader) {
    try {
        val companion = recomposerCompanion(loader)
        companion.type.methods
            .singleOrNull { it.name.contains("setHotReloadEnabled") }
            ?.apply { isAccessible = true; invoke(companion.instance, true) }

        logger.debug("'setHotReloadEnabled' method found, enabled compose hot reload mode ($loader)")
    } catch (e: ReflectiveOperationException) {
        logger.warn("Failed to enable compose hot reload mode ($loader)", e)
    }
}

/**
 * Fine-grained invalidation: invalidates only the Compose groups matching [key]
 * (the Compose-compiler group key).
 */
fun invalidateGroupsWithKey(loader: ClassLoader, key: Int) {
    try {
        val companion = recomposerCompanion(loader)
        companion.type.methods
            .singleOrNull { it.name.contains("invalidateGroupsWithKey") }
            ?.apply { isAccessible = true; invoke(companion.instance, key) }
    } catch (e: ReflectiveOperationException) {
        logger.warn("Failed to invalidate groups with key $key ($loader)", e)
    }
}

/**
 * Forces a full composition teardown and rebuild ("hard" reload).
 *
 * Unlike [invalidateGroupsWithKey] — which only invalidates changed groups and, crucially, *skips*
 * any recomposer whose current error is non-recoverable — this disposes every running recomposer's
 * compositions and recomposes them from scratch (Compose's 'saveStateAndDisposeForHotReload' followed
 * by 'loadStateAndComposeForHotReload', which resets all error states).
 */
fun reloadCompositionState(loader: ClassLoader) {
    val companion = try {
        recomposerCompanion(loader)
    } catch (e: ReflectiveOperationException) {
        logger.warn("'Recomposer.Companion' not found ($loader)", e)
        return
    }

    val save = companion.type.methods
        .singleOrNull { it.name.contains("saveStateAndDisposeForHotReload") }
    val load = companion.type.methods
        .singleOrNull { it.name.contains("loadStateAndComposeForHotReload") }

    if (save == null || load == null) {
        logger.warn("'saveStateAndDisposeForHotReload'/'loadStateAndComposeForHotReload' not found ($loader)")
        return
    }

    val token = try {
        save.isAccessible = true
        save.invoke(companion.instance)
    } catch (t: InvocationTargetException) {
        logger.warn("'saveStateAndDisposeForHotReload' failed ($loader)", t.cause ?: t)
        return
    }

    try {
        load.isAccessible = true
        load.invoke(companion.instance, token)
    } catch (t: InvocationTargetException) {
        logger.warn("'loadStateAndComposeForHotReload' completed with a trailing error ($loader)", t.cause ?: t)
    }
}
