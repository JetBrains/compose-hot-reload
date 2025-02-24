/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.agent

import javassist.ClassPool
import javassist.LoaderClassPath
import org.jetbrains.compose.reload.analysis.ClassId
import org.jetbrains.compose.reload.analysis.RuntimeInfo
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.lang.instrument.ClassDefinition
import java.lang.instrument.Instrumentation
import java.util.UUID

private val logger = createLogger()

private val pool = ClassPool().apply {
    appendClassPath(LoaderClassPath(ClassLoader.getSystemClassLoader()))
}

data class Reload(
    val reloadRequestId: UUID,
    val classes: List<ClassDefinition>,
    val previousRuntime: RuntimeInfo,
    val newRuntime: RuntimeInfo,
)

internal fun reload(
    instrumentation: Instrumentation,
    reloadRequestId: UUID,
    pendingChanges: Map<File, OrchestrationMessage.ReloadClassesRequest.ChangeType>
): Reload {
    val definitions = pendingChanges.mapNotNull { (file, change) ->
        if (change == OrchestrationMessage.ReloadClassesRequest.ChangeType.Removed) {
            logger.info("Removed: $file")
            return@mapNotNull null
        }

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
        val clazz = pool.makeClass(code.inputStream())

        val originalClass = runCatching {
            val loader = findClassLoader(ClassId.fromFqn(clazz.name)).get()
            loader?.loadClass(clazz.name)
        }.getOrNull()

        if (originalClass == null) {
            logger.info("Class '${clazz.name}' was not loaded yet")
            return@mapNotNull null
        }

        logger.orchestration(buildString {
            appendLine("Reloading class: '${clazz.name}' (${change.name})")

            if (originalClass.superclass?.name != clazz.superclass.name) {
                appendLine("⚠️ Superclass: '${originalClass?.superclass?.name}' -> '${clazz.superclass?.name}'")
            }

            val addedInterfaces = clazz.interfaces.map { it.name } -
                originalClass.interfaces?.map { it.name }.orEmpty()
            addedInterfaces.forEach { addedInterface ->
                appendLine("⚠️ +Interface: '$addedInterface'")
            }

            val removedInterfaces = originalClass?.interfaces.orEmpty().map { it.name }.toSet() -
                clazz.interfaces.map { it.name }.toSet()
            removedInterfaces.forEach { removedInterface ->
                appendLine("⚠️ -Interface: '$removedInterface'")
            }
        }.trim())

        clazz.transformForStaticsInitialization(originalClass)

        val baos = ByteArrayOutputStream()
        val daos = DataOutputStream(baos)
        clazz.classFile.write(daos)

        ClassDefinition(originalClass, baos.toByteArray())
    }

    instrumentation.redefineClasses(*definitions.toTypedArray())
    val (previousRuntime, newRuntime) = redefineRuntimeInfo().get()

    reinitializeStaticsIfNecessary(definitions, previousRuntime, newRuntime)

    return Reload(reloadRequestId, definitions, previousRuntime, newRuntime)
}
