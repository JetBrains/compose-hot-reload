/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.agent

import org.jetbrains.compose.reload.analysis.ClassId
import org.jetbrains.compose.reload.analysis.RuntimeDirtyScopes
import org.jetbrains.compose.reload.core.Try
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.mapLeft
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.lang.instrument.ClassDefinition
import java.lang.instrument.Instrumentation
import java.util.UUID

private val logger = createLogger()

data class Reload(
    val reloadRequestId: UUID,
    val definitions: List<ClassDefinition>,
    val dirtyRuntime: RuntimeDirtyScopes,
)

internal fun reload(
    instrumentation: Instrumentation,
    reloadRequestId: UUID,
    pendingChanges: Map<File, OrchestrationMessage.ReloadClassesRequest.ChangeType>
): Try<Reload> = Try {
    val definitions = pendingChanges.mapNotNull { (file, change) ->
        if (change == OrchestrationMessage.ReloadClassesRequest.ChangeType.Removed) {
            logger.info("Removed: $file")
            return@mapNotNull null
        }

        logger.info("${change.name}:  $file")

        if (file.extension != "class") {
            logger.warn("$change: $file is not a class")
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
            logger.info("Class '$classId' is not loaded yet")
            return@mapNotNull null
        }

        val originalClass = runCatching {
            loader.loadClass(classId.toFqn())
        }.getOrNull()

        val transformed = runCatching {
            val clazz = getClassPool(loader).makeClass(code.inputStream())

            if (originalClass == null) {
                logger.info("Class '${clazz.name}' was not loaded yet")
                return@mapNotNull null
            }

            logger.orchestration(buildString {
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
            logger.orchestration("Failed to transform '${originalClass?.name}'", failure)
            code
        }

        ClassDefinition(originalClass, transformed)
    }

    instrumentation.redefineClasses(*definitions.toTypedArray())
    return redefineRuntimeInfo().get().mapLeft { redefinition ->
        val reload = Reload(reloadRequestId, definitions, redefinition)
        reinitializeStaticsIfNecessary(reload)
        reload
    }
}
