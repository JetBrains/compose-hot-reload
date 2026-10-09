/*
 * Copyright 2024-2026 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.shared

import java.lang.reflect.InvocationTargetException

private class RecomposerCompanion(val instance: Any?, val type: Class<*>)

private fun recomposerCompanion(loader: ClassLoader): RecomposerCompanion {
    val recomposerClass = loader.loadClass("androidx.compose.runtime.Recomposer")
    val recomposerCompanion = recomposerClass.getField("Companion").get(null)
    val recomposerCompanionClass = loader.loadClass("androidx.compose.runtime.Recomposer\$Companion")
    return RecomposerCompanion(recomposerCompanion, recomposerCompanionClass)
}

/**
 * Puts Compose into hot-reload mode on [loader] ('Recomposer.setHotReloadEnabled(true)').
 *
 * @throws ReflectiveOperationException if the Compose runtime cannot be accessed through [loader]
 */
fun enableComposeHotReloadMode(loader: ClassLoader) {
    val companion = recomposerCompanion(loader)
    companion.type.methods
        .singleOrNull { it.name.contains("setHotReloadEnabled") }
        ?.apply { isAccessible = true; invoke(companion.instance, true) }
}

/**
 * Fine-grained invalidation: invalidates only the Compose groups matching [key]
 * (the Compose-compiler group key).
 *
 * @throws ReflectiveOperationException if the Compose runtime cannot be accessed through [loader]
 */
fun invalidateGroupsWithKey(loader: ClassLoader, key: Int) {
    val companion = recomposerCompanion(loader)
    companion.type.methods
        .singleOrNull { it.name.contains("invalidateGroupsWithKey") }
        ?.apply { isAccessible = true; invoke(companion.instance, key) }
}

/**
 * Forces a full composition teardown and rebuild ("hard" reload).
 *
 * Unlike [invalidateGroupsWithKey] — which only invalidates changed groups and, crucially, *skips*
 * any recomposer whose current error is non-recoverable — this disposes every running recomposer's
 * compositions and recomposes them from scratch (Compose's 'saveStateAndDisposeForHotReload' followed
 * by 'loadStateAndComposeForHotReload', which resets all error states).
 *
 * @param warn Reports problems; the platform decides how to log them.
 */
fun reloadCompositionState(loader: ClassLoader, warn: (message: String, cause: Throwable?) -> Unit) {
    val companion = try {
        recomposerCompanion(loader)
    } catch (e: ReflectiveOperationException) {
        warn("'Recomposer.Companion' not found", e)
        return
    }

    val save = companion.type.methods
        .singleOrNull { it.name.contains("saveStateAndDisposeForHotReload") }
    val load = companion.type.methods
        .singleOrNull { it.name.contains("loadStateAndComposeForHotReload") }

    if (save == null || load == null) {
        warn("'saveStateAndDisposeForHotReload'/'loadStateAndComposeForHotReload' not found", null)
        return
    }

    val token = try {
        save.isAccessible = true
        save.invoke(companion.instance)
    } catch (t: InvocationTargetException) {
        warn("'saveStateAndDisposeForHotReload' failed", t.cause ?: t)
        return
    }

    try {
        load.isAccessible = true
        load.invoke(companion.instance, token)
    } catch (t: InvocationTargetException) {
        warn("'loadStateAndComposeForHotReload' completed with a trailing error", t.cause ?: t)
    }
}
