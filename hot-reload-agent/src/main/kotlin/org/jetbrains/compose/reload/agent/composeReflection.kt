/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.agent

import org.jetbrains.compose.reload.analysis.ComposeGroupKey
import org.jetbrains.compose.reload.core.createLogger

private val logger = createLogger()
private const val RECOMPOSER_CLASS = "androidx.compose.runtime.Recomposer"
private const val COMPANION_FIELD = "Companion"
private const val RECOMPOSER_COMPANION_CLASS = "$RECOMPOSER_CLASS\$$COMPANION_FIELD"

internal fun enableComposeHotReloadMode(moduleClassLoader: ClassLoader = Thread.currentThread().contextClassLoader) {
    try {
        val recomposerClass = moduleClassLoader.loadClass(RECOMPOSER_CLASS)
        val recomposerCompanion = recomposerClass.getField(COMPANION_FIELD).get(null)
        val recomposerCompanionClass = moduleClassLoader.loadClass(RECOMPOSER_COMPANION_CLASS)
        recomposerCompanionClass.methods
            .singleOrNull { it.name.contains("setHotReloadEnabled") }
            ?.apply { invoke(recomposerCompanion, true) }

        logger.debug("'setHotReloadEnabled' method found, enabled compose hot reload mode")
    } catch (e: ReflectiveOperationException) {
        logger.warn("Failed to enable compose hot reload mode", e)
    }
}

internal fun invalidateGroupsWithKey(key: ComposeGroupKey) {
    val recomposerClass = Thread.currentThread().contextClassLoader.loadClass(RECOMPOSER_CLASS)
    val recomposerCompanion = recomposerClass.getField(COMPANION_FIELD).get(null)
    val recomposerCompanionClass = Thread.currentThread().contextClassLoader.loadClass(RECOMPOSER_COMPANION_CLASS)

    recomposerCompanionClass.methods
        .singleOrNull { it.name.contains("invalidateGroupsWithKey") }
        ?.apply { invoke(recomposerCompanion, key.key) }
}
