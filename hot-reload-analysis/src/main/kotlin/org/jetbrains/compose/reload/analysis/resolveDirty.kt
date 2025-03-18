/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.analysis

import org.jetbrains.compose.reload.core.HotReloadEnvironment
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.simpleName
import kotlin.time.measureTimedValue

private val logger = createLogger()

data class RuntimeDirtyScopes(
    val redefinedClasses: List<ClassInfo>,
    val dirtyScopes: List<RuntimeScopeInfo>
) {
    val dirtyMethodIds = dirtyScopes.groupBy { it.methodId }
}

fun RuntimeInfo.resolveDirtyRuntimeScopes(redefined: RuntimeInfo): RuntimeDirtyScopes {
    val (redefinition, duration) = measureTimedValue {
        RuntimeDirtyScopes(
            redefinedClasses = redefined.classIndex.values.toList(),
            dirtyScopes = resolveDirtyRuntimeScopeInfos(redefined)
        )
    }

    logger.info("${simpleName<RuntimeDirtyScopes>()} resolved in [${duration}]")
    return redefinition
}

private fun RuntimeInfo.resolveDirtyRuntimeScopeInfos(redefined: RuntimeInfo): List<RuntimeScopeInfo> {
    val dirtyComposeScopes = resolveDirtyComposeScopes(redefined)
    val dirtyMethods = resolveDirtyMethods(redefined)
    val dirtyFields = resolveDirtyFields(redefined)
    val transitivelyDirty = resolveTransitivelyDirty(redefined, dirtyMethods, dirtyFields)

    return buildSet {
        addAll(dirtyMethods.map { it.rootScope })
        addAll(dirtyComposeScopes)
        addAll(transitivelyDirty)
    }.toList()
}

private fun RuntimeInfo.resolveDirtyMethods(redefined: RuntimeInfo): List<MethodInfo> {
    return redefined.methodIndex.mapNotNull { (methodId, redefinedMethod) ->
        val previousMethod = methodIndex[methodId] ?: return@mapNotNull redefinedMethod
        if (previousMethod.rootScope.hash != redefinedMethod.rootScope.hash) {
            return@mapNotNull redefinedMethod
        }
        null
    }
}

private fun RuntimeInfo.resolveDirtyFields(redefined: RuntimeInfo): List<FieldInfo> {
    return redefined.fieldIndex.mapNotNull { (fieldId, redefinedField) ->
        val previousField = fieldIndex[fieldId] ?: return@mapNotNull redefinedField
        if (previousField.initialValue != redefinedField.initialValue) {
            return@mapNotNull redefinedField
        }
        null
    }
}

private fun RuntimeInfo.resolveDirtyComposeScopes(redefined: RuntimeInfo): List<RuntimeScopeInfo> {
    val result = mutableListOf<RuntimeScopeInfo>()
    redefined.groupIndex.forEach forEachGroup@{ (groupKey, redefinedGroup) ->
        if (groupKey == null) return@forEachGroup
        if (SpecialComposeGroupKeys.isRememberGroup(groupKey)) return@forEachGroup

        groupIndex[groupKey]?.let { originalGroup ->
            val originalGroupInvalidationKey = originalGroup.invalidationKey()
            val redefinedGroupInvalidationKey = redefinedGroup.invalidationKey()
            if (originalGroupInvalidationKey != redefinedGroupInvalidationKey) {
                result.addAll(redefinedGroup)
            }
        }
    }
    return result
}

/**
 * This method will start at the [dirtyFields] and [dirtyMethods] provided by it and
 * walks the dependency graph in [RuntimeInfo] (e.g. [RuntimeInfo.dependencyIndex] or [RuntimeInfo.superIndex]).
 * Visited elements therefore depend on dirty scopes and are therefore also marked as dirty.
 *
 * This algorithm is configured to only search the graph using a maximum depth
 * (configured by [org.jetbrains.compose.reload.core.HotReloadProperty.DirtyResolveDepthLimit]).
 */
private fun RuntimeInfo.resolveTransitivelyDirty(
    redefined: RuntimeInfo,
    dirtyMethods: List<MethodInfo>,
    dirtyFields: List<FieldInfo>,
): List<RuntimeScopeInfo> {

    class Element(val memberId: MemberId, val depth: Int)

    val queue = ArrayDeque<Element>()
    val visited = hashMapOf<MemberId, Element>()
    val result = mutableListOf<RuntimeScopeInfo>()

    /* Setup initial queue */
    dirtyMethods.forEach { dirtyMethod ->
        queue.add(Element(dirtyMethod.methodId, depth = 0))
    }

    dirtyFields.forEach { dirtyField ->
        queue.add(Element(dirtyField.fieldId, depth = 0))
    }

    /* Resolve Loop */
    while (queue.isNotEmpty()) {
        val element = queue.removeFirst()
        val previous = visited.put(element.memberId, element)

        /* If we have seen this member already (with higher or equal depth), then we skip it */
        if (previous != null && previous.depth <= element.depth) continue

        /* This element gets rejected: We reached our depth limit */
        if (element.depth > HotReloadEnvironment.dirtyResolveDepthLimit) {
            continue
        }

        /**
         * Marks the [scopeInfo] as 'dirty' (aka will put it in the result set)
         * and will enqueue the associated member for further traversal if necessary.
         */
        fun markDirty(scopeInfo: RuntimeScopeInfo) {
            result.add(scopeInfo)

            /**
             * We do not follow the graph when the scope has an associated Compose group
             * Composables calling into other Composables will not mark the calle as dirty
             * if the called Composable is dirty.
             */
            if (scopeInfo.group == null) {
                queue.add(Element(scopeInfo.methodId, depth = element.depth + 1))
            }
        }

        /* Get dependencies from current runtime */
        dependencyIndex[element.memberId].orEmpty()
            .filter { scope -> scope.methodId.classId !in redefined.classIndex }
            .forEach { scope -> markDirty(scope) }

        /* Get dependencies form redefined classes */
        redefined.dependencyIndex[element.memberId].orEmpty()
            .forEach { scope -> markDirty(scope) }

        /* Dirty classInitializer -> dirty static fields */
        if (element.memberId is MethodId && element.memberId.isClassInitializer) {
            val classInfo = redefined.classIndex[element.memberId.classId] ?: classIndex[element.memberId.classId]
            classInfo?.fields?.forEach { (fieldId, field) ->
                if (field.isStatic) {
                    queue.add(Element(fieldId, depth = element.depth + 1))
                }
            }
        }

        /* Let's walk from implementation methods to their corresponding super method */
        if (element.memberId is MethodId && HotReloadEnvironment.virtualMethodResolveEnabled) run virtualMethodResolve@{
            val classId = element.memberId.classId
            val superClasses = if (classId in redefined.classIndex) redefined.superIndex[classId]
            else superIndex[classId]

            superClasses?.forEach { superClassId ->
                val superMethodDescriptor = element.memberId.copy(superClassId)
                queue.add(Element(superMethodDescriptor, depth = element.depth + 1))
            }
        }
    }

    return result
}

private fun RuntimeScopeInfo.invalidationKey(): Long {
    var result = hash.value

    fun push(value: Long) {
        result = 31L * result + value
    }

    /*
    Special Case when 'OptimizeNonSkippingGroups' is not enabled:
    In this case calls to 'remember {}' will generate a separate group with a known group key.
    We do want to include such groups into the parent scope (as having them standalone will not make a lot of sense)
    Therefore, we push hash of those values into the invalidation key of this parent
     */
    children.forEach { child ->
        if (child.group != null && SpecialComposeGroupKeys.isRememberGroup(child.group)) {
            push(child.hash.value)
        }
    }
    return result
}

private fun Iterable<RuntimeScopeInfo>.invalidationKey(): Long = fold(0L) { acc, scope ->
    31L * acc + scope.invalidationKey()
}
