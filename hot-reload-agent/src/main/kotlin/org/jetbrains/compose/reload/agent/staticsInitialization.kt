/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.agent

import javassist.CtClass
import javassist.CtConstructor
import org.jetbrains.compose.reload.analysis.classId
import org.jetbrains.compose.reload.analysis.classInitializerMethodId
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.debug
import org.jetbrains.compose.reload.core.sortedByTopology
import org.jetbrains.compose.reload.core.warn
import java.lang.reflect.Modifier

private val logger = createLogger()

internal const val reinitializeName = "\$chr\$clinit"

/**
 * Re-initialize changed static
 * 1) We demote 'static final' to 'non-final'
 * 2) We create a static function which contains the class initializer body
 * 3) We call the new static function to re-initialize the class.
 */
internal fun CtClass.transformForStaticsInitialization(originalClass: Class<*>?) {
    if (originalClass == null) return
    /**
     * Static fields on interfaces have to be final!
     * Therefore, we disable the re-initialization for interfaces.
     * If re-initialization is desired, either JBR support or more advanced class rewriting is required.
     */
    if (this.isInterface) return

    val clazzInitializer = classInitializer ?: return

    declaredFields.forEach { field ->
        field.modifiers = field.modifiers and Modifier.FINAL.inv()
    }

    logger.debug("Created synthetic re-initializer for '${name}")
    val reinit = CtConstructor(clazzInitializer, this, null)
    reinit.methodInfo.name = reinitializeName
    reinit.modifiers = Modifier.PUBLIC or Modifier.STATIC
    addConstructor(reinit)
}

/**
 * Will use the [previousRuntime] and [newRuntime] to infer which of the re-defined classes require
 * to re-initialize the statics.
 */
internal fun reinitializeStaticsIfNecessary(reload: Reload) {
    /* Step 1: First, let us identify which clinits actually changed and shall be re-executed */
    val dirtyClasses = reload.definitions.mapNotNull { classDefinition ->
        val clazz = classDefinition.definitionClass
        val clinitMethodId = clazz.classId.classInitializerMethodId

        if (clinitMethodId in reload.dirty.dirtyMethodIds) {
            return@mapNotNull clazz
        }

        null
    }

    if (dirtyClasses.isEmpty()) {
        logger.debug("No class initializers were changed")
        return
    }

    val dirtyClassIds = dirtyClasses.associateBy { it.classId }

    val classInitializerDependencies = dirtyClasses.associateWith { clazz ->
        reload.dirty.dirtyMethodIds[clazz.classId.classInitializerMethodId].orEmpty()
            .flatMap { scope -> scope.methodDependencies }
            .mapNotNull { dependencyMethodId -> dirtyClassIds[dependencyMethodId.classId].takeIf { it != clazz } }
    }

    /*
    Step 2: Let us ensure we're executing the initializers in a reasonable order:
    if class 'A' depends on 'B'
     */
    val topologicallySortedDirtyClasses = dirtyClasses.sortedByTopology(
        onCycle = { logger.warn("<clinit> cycle detected: ${it.joinToString(", ") { it.simpleName }}") },
        edges = { clazz -> classInitializerDependencies[clazz].orEmpty() }
    )

    /**
     * Execute the re-initialization in reverse order:
     *
     * ```
     *     A
     *    / \    // A depends on B and C
     *   B   C
     * ```
     *
     * -> Initialize B, C and finally A
     *
     */
    topologicallySortedDirtyClasses.asReversed().forEach { clazz ->
        clazz.reinitializeStaticsIfNecessary()
    }
}

private fun Class<*>.reinitializeStaticsIfNecessary() {
    val reinit = runCatching { getDeclaredMethod(reinitializeName) }.getOrNull() ?: return
    logger.debug("Re-initializing '$name'")
    reinit.trySetAccessible()
    reinit.invoke(null)
    logger.debug("Re-initialized '$name'")
}
