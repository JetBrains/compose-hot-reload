/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.agent

import org.jetbrains.compose.reload.analysis.ClassId
import org.jetbrains.compose.reload.analysis.ResolvedDirtyScopes
import org.jetbrains.compose.reload.core.Context
import org.jetbrains.compose.reload.core.Try
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.debug
import org.jetbrains.compose.reload.core.error
import org.jetbrains.compose.reload.core.isClass
import org.jetbrains.compose.reload.core.mapLeft
import org.jetbrains.compose.reload.core.warn
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ReloadClassesRequest
import org.jetbrains.compose.reload.orchestration.OrchestrationMessageId
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.lang.instrument.ClassDefinition
import java.lang.instrument.Instrumentation

private val logger = createLogger()

data class Reload(
    val reloadRequestId: OrchestrationMessageId,
    val definitions: List<ClassDefinition>,
    val dirty: ResolvedDirtyScopes,
)

internal fun Context.reload(
    instrumentation: Instrumentation,
    reloadRequestId: OrchestrationMessageId,
    pendingChanges: Map<File, ReloadClassesRequest.ChangeType>
): Try<Reload> = Try {

    val definitions = pendingChanges.mapNotNull { (file, change) ->
        if (change == ReloadClassesRequest.ChangeType.Removed) {
            logger.debug("Removed: $file")
            return@mapNotNull null
        }

        logger.debug("${change.name}:  $file")

        if (!file.isClass()) {
            return@mapNotNull null
        }

        if (!file.isFile) {
            logger.warn("$change: $file is not a regular file")
            return@mapNotNull null
        }

        logger.debug("Loading: $file")
        val code = file.readBytes()

        val classId = ClassId(code)
        if (classId == null) {
            logger.error("Cannot infer 'ClassId' for '$file'")
            return@mapNotNull null
        }

        val loader = findClassLoader(classId).get()
        if (loader == null) {
            logger.debug("Class '$classId' is not loaded yet")
            return@mapNotNull null
        }

        val originalClass = runCatching {
            loader.loadClass(classId.toFqn())
        }.getOrNull()

        val transformed = runCatching {
            val clazz = getClassPool(loader).makeClass(code.inputStream())

            if (originalClass == null) {
                logger.debug("Class '${clazz.name}' was not loaded yet")
                return@mapNotNull null
            }

            logger.debug(buildString {
                appendLine("Reloading class: '${clazz.name}' (${change.name})")

                if (originalClass.superclass?.name != clazz.superclass.name) {
                    appendLine("⚠️ Superclass: '${originalClass.superclass?.name}' -> '${clazz.superclass?.name}'")
                }

                val addedInterfaces = clazz.interfaces.map { it.name }.toSet() -
                    originalClass.interfaces.map { it.name }.toSet()
                addedInterfaces.forEach { addedInterface ->
                    appendLine("⚠️ +Interface: '$addedInterface'")
                }

                val removedInterfaces = originalClass.interfaces.orEmpty().map { it.name }.toSet() -
                    clazz.interfaces.map { it.name }.toSet()
                removedInterfaces.forEach { removedInterface ->
                    appendLine("⚠️ -Interface: '$removedInterface'")
                }
            }.trim())

            clazz.transformForStaticsInitialization(originalClass)

            val baos = ByteArrayOutputStream()
            val daos = DataOutputStream(baos)
            clazz.classFile.write(daos)
            baos.toByteArray()
        }.getOrElse { failure ->
            logger.error("Failed to transform '${originalClass?.name}'", failure)
            code
        }

        ClassDefinition(originalClass, transformed)
    }

    instrumentation.redefineClasses(*definitions.toTypedArray())
    return redefineApplicationInfo().get().mapLeft { redefinition ->
        val reload = Reload(reloadRequestId, definitions, redefinition)
        reinitializeStaticsIfNecessary(reload)
        cleanResourceCacheIfNecessary()
        reload
    }
}
