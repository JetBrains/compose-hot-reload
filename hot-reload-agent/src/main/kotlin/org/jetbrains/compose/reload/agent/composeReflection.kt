/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.agent

import org.jetbrains.compose.reload.analysis.ClassId
import org.jetbrains.compose.reload.analysis.ComposeGroupKey
import org.jetbrains.compose.reload.core.createLogger
import java.lang.instrument.ClassFileTransformer
import java.lang.ref.WeakReference
import java.security.ProtectionDomain
import javax.swing.SwingUtilities

private val logger = createLogger()
private const val RECOMPOSER_CLASS = "androidx.compose.runtime.Recomposer"
private val recomposerClassId = ClassId.fromFqn(RECOMPOSER_CLASS)
private const val COMPANION_FIELD = "Companion"
private const val RECOMPOSER_COMPANION_CLASS = "$RECOMPOSER_CLASS\$$COMPANION_FIELD"

private val composeClassLoaders = mutableListOf<WeakReference<ClassLoader>>()

internal fun enableComposeHotReloadMode(loader: ClassLoader) {
    try {
        val recomposerClass = loader.loadClass(RECOMPOSER_CLASS)
        val recomposerCompanion = recomposerClass.getField(COMPANION_FIELD).get(null)
        val recomposerCompanionClass = loader.loadClass(RECOMPOSER_COMPANION_CLASS)
        recomposerCompanionClass.methods
            .singleOrNull { it.name.contains("setHotReloadEnabled") }
            ?.apply { invoke(recomposerCompanion, true) }

        logger.debug("'setHotReloadEnabled' method found, enabled compose hot reload mode")
    } catch (e: ReflectiveOperationException) {
        logger.warn("Failed to enable compose hot reload mode", e)
    }
}

internal fun invalidateGroupsWithKey(key: ComposeGroupKey) {
    val loaders = synchronized(composeClassLoaders) { composeClassLoaders.mapNotNull { it.get() } }

    loaders.forEach { loader ->
        val recomposerClass = loader.loadClass(RECOMPOSER_CLASS)
        val recomposerCompanion = recomposerClass.getField(COMPANION_FIELD).get(null)
        val recomposerCompanionClass = loader.loadClass(RECOMPOSER_COMPANION_CLASS)

        recomposerCompanionClass.methods
            .singleOrNull { it.name.contains("invalidateGroupsWithKey") }
            ?.apply { invoke(recomposerCompanion, key.key) }
    }
}


internal object ComposeTransformer : ClassFileTransformer {
    override fun transform(
        loader: ClassLoader?, className: String?,
        classBeingRedefined: Class<*>?,
        protectionDomain: ProtectionDomain?, classfileBuffer: ByteArray?
    ): ByteArray? {

        if (className == recomposerClassId.value) {
            synchronized(composeClassLoaders) {
                composeClassLoaders.add(WeakReference(loader ?: ClassLoader.getSystemClassLoader()))
            }

            SwingUtilities.invokeLater {
                enableComposeHotReloadMode(loader ?: ClassLoader.getSystemClassLoader())
            }
        }
        return null
    }
}
