package org.jetbrains.compose.reload.agent

import javassist.CtClass
import javassist.CtConstructor
import org.jetbrains.compose.reload.analysis.RuntimeInfo
import org.jetbrains.compose.reload.analysis.classId
import org.jetbrains.compose.reload.analysis.classInitializerMethodId
import org.jetbrains.compose.reload.analysis.resolveInvalidationKey
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.topologicalSort
import java.lang.instrument.ClassDefinition
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

    val originalFields = originalClass.declaredFields.associate { field ->
        field.name to field.type.name
    }

    val newFields = declaredFields.associate { field ->
        field.name to field.type.name
    }

    val clazzInitializer = classInitializer
    if (clazzInitializer != null && originalFields != newFields) {
        declaredFields.forEach { field ->
            field.modifiers = field.modifiers and Modifier.FINAL.inv()
        }

        logger.debug("Created synthetic re-initializer for '${name}")
        val reinit = CtConstructor(clazzInitializer, this, null)
        reinit.methodInfo.name = reinitializeName
        reinit.modifiers = Modifier.PUBLIC or Modifier.STATIC
        addConstructor(reinit)
    }
}

/**
 * Will use the [previousRuntime] and [newRuntime] to infer which of the re-defined classes require
 * to re-initialize the statics.
 */
internal fun reinitializeStaticsIfNecessary(
    classDefinition: List<ClassDefinition>, previousRuntime: RuntimeInfo, newRuntime: RuntimeInfo
) {
    /* Step 1: First, let us identify which clinits actually changed and shall be re-executed */
    val dirtyClasses = classDefinition.mapNotNull { classDefinition ->
        val clazz = classDefinition.definitionClass
        val clinitMethodId = clazz.classId.classInitializerMethodId

        val previousKey = previousRuntime.resolveInvalidationKey(clinitMethodId) ?: return@mapNotNull clazz
        val newKey = newRuntime.resolveInvalidationKey(clinitMethodId) ?: return@mapNotNull null
        if (previousKey == newKey) return@mapNotNull null
        clazz
    }

    if (dirtyClasses.isEmpty()) {
        logger.debug("No class initializers were changed")
        return
    }

    val dirtyClassIds = dirtyClasses.associateBy { it.classId }

    val classInitializerDependencies = dirtyClasses.associateWith { clazz ->
        newRuntime.methods[clazz.classId.classInitializerMethodId].orEmpty()
            .flatMap { it.dependencies }
            .mapNotNull { dependencyMethodId -> dirtyClassIds[dependencyMethodId.classId].takeIf { it != clazz } }
    }

    /*
    Step 2: Let us ensure we're executing the initializers in a reasonable order:
    if class 'A' depends on 'B'
     */
    val topologicallySortedDirtyClasses = dirtyClasses.topologicalSort(
        onCycle = { logger.warn("<clinit> cycle detected: ${it.joinToString(", ") { it.simpleName }}") },
        follows = { clazz -> classInitializerDependencies[clazz].orEmpty() }
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
