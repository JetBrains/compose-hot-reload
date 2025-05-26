/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.agent

import org.jetbrains.compose.reload.analysis.ComposeGroupKey
import org.jetbrains.compose.reload.analysis.Ids
import org.jetbrains.compose.reload.core.isFailure
import org.jetbrains.compose.reload.orchestration.HotReloadLogger
import java.lang.instrument.ClassFileTransformer
import java.lang.instrument.Instrumentation
import java.security.ProtectionDomain
import java.util.WeakHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private val logger = HotReloadLogger()

private val composeClassLoadersLock = ReentrantLock()
private val composeClassLoaders = WeakHashMap<ClassLoader, Unit>()

internal fun launchComposeInstrumentation(instrumentation: Instrumentation) {
    launchComposeGroupInvalidation()
    instrumentation.addTransformer(ComposeTransformer)
}

private object ComposeTransformer : ClassFileTransformer {
    override fun transform(
        loader: ClassLoader?, className: String?,
        classBeingRedefined: Class<*>?, protectionDomain: ProtectionDomain?, classfileBuffer: ByteArray?
    ): ByteArray? {
        if (className != Ids.Recomposer.classId.value &&
            className != Ids.Composer.classId.value
        ) return null

        composeClassLoadersLock.withLock {
            val loader = loader ?: ClassLoader.getSystemClassLoader()
            if (composeClassLoaders.put(loader, Unit) == null) {
                /* put returning null -> loader was not known before, so we can setup copose */
                runOnMainThread {
                    enableComposeHotReloadMode(loader)
                }
            }
        }

        return null
    }
}

private fun launchComposeGroupInvalidation() {

    /*
    Instruct Compose to invalidate groups that have changed, after successful reload.
     */
    invokeAfterHotReload { reloadRequestId, result ->
        if (result.isFailure()) return@invokeAfterHotReload

        val invalidations = result.value.dirtyRuntime.dirtyScopes
            .filter { scope -> scope.group != null }
            .groupBy { it.group }

        if (invalidations.isEmpty()) {
            logger.info("All groups retained")
        }

        invalidations.forEach { group, methods ->
            if (group == null) return@forEach

            val methods = methods.map { it.methodId }.toSet()
                .joinToString(", ", prefix = "(", postfix = ")") { methodId ->
                    "${methodId.classId}.${methodId.methodName}"
                }

            logger.info("Invalidating group '${group.key}' $methods")
            invalidateGroupsWithKey(group)
        }
    }
}

private fun enableComposeHotReloadMode(loader: ClassLoader) {
    try {
        val recomposerClass = loader.loadClass(Ids.Recomposer.classId.toFqn())
        val recomposerCompanion = recomposerClass.getField(Ids.Recomposer.companion.fieldName).get(null)
        val recomposerCompanionClass = loader.loadClass(Ids.Recomposer.Companion.classId.toFqn())
        recomposerCompanionClass.methods
            .singleOrNull { it.name.contains("setHotReloadEnabled") }
            ?.apply { invoke(recomposerCompanion, true) }

        logger.debug("'setHotReloadEnabled' method found, enabled compose hot reload mode (${loader.name})")
    } catch (e: ReflectiveOperationException) {
        logger.warn("Failed to enable compose hot reload mode (${loader.name})", e)
    }
}

private fun invalidateGroupsWithKey(key: ComposeGroupKey) {
    composeClassLoadersLock.withLock {
        composeClassLoaders.keys.forEach { loader ->
            invalidateGroupsWithKey(loader, key)
        }
    }
}

private fun invalidateGroupsWithKey(loader: ClassLoader, key: ComposeGroupKey) {
    val recomposerClass = loader.loadClass(Ids.Recomposer.classId.toFqn())
    val recomposerCompanion = recomposerClass.getField(Ids.Recomposer.companion.fieldName).get(null)
    val recomposerCompanionClass = loader.loadClass(Ids.Recomposer.Companion.classId.toFqn())

    recomposerCompanionClass.methods
        .singleOrNull { it.name.contains("invalidateGroupsWithKey") }
        ?.apply { invoke(recomposerCompanion, key.key) }
}
