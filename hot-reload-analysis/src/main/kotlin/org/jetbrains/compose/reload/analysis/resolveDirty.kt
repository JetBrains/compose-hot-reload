/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.analysis

import org.jetbrains.compose.reload.core.HotReloadEnvironment
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.error
import org.jetbrains.compose.reload.core.info
import org.jetbrains.compose.reload.core.simpleName
import org.jetbrains.compose.reload.core.withClosure
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
    val dirtyComposeScopes = resolveDirtyComposeScopes(redefined) + resolveRemovedComposeScopes(redefined)
    val dirtyMethods = resolveDirtyMethods(redefined) + resolveRemovedMethods(redefined)
    val dirtyFields = resolveDirtyFields(redefined) + resolveRemovedFields(redefined)
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
        if (previousMethod.rootScope.children.map { it.group } != redefinedMethod.rootScope.children.map { it.group }) {
            return@mapNotNull redefinedMethod
        }
        null
    }
}

private fun RuntimeInfo.resolveRemovedMethods(redefined: RuntimeInfo): List<MethodInfo> {
    return redefined.classIndex.flatMap { (classId, redefinedClass) ->
        val previousClass = classIndex[classId] ?: return@flatMap emptyList()
        previousClass.methods.mapNotNull { (methodId, method) ->
            if (methodId in redefinedClass.methods) return@mapNotNull null
            else method
        }
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

private fun RuntimeInfo.resolveRemovedFields(redefined: RuntimeInfo): List<FieldInfo> {
    return redefined.classIndex.flatMap { (classId, redefinedClass) ->
        val previousClass = classIndex[classId] ?: return@flatMap emptyList()
        previousClass.fields.mapNotNull { (fieldId, field) ->
            if (fieldId in redefinedClass.fields) return@mapNotNull null
            else field
        }
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

private fun RuntimeInfo.resolveRemovedComposeScopes(redefined: RuntimeInfo): List<RuntimeScopeInfo> {
    val result = mutableListOf<RuntimeScopeInfo>()
    redefined.methodIndex.forEach forEachMethod@{ (methodId, redefinedMethod) ->
        val previousMethod = methodIndex[methodId] ?: return@forEachMethod
        val redefinedScopeGroups = redefinedMethod.allScopes.map { it.group }
        previousMethod.allScopes.forEach { previousScope ->
            if (previousScope.group !in redefinedScopeGroups) result.add(previousScope)
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

    /*
    The dirty resolution typically starts with known dirty methods or fields.
    Those 'starting points' will be followed to mark dependent code as dirty as well (transitively).
    Some special classes, or some special code, however, can be ignored as a starting point:
    e.g., `ComposableSingletons` are considered an intermediate hub generated by the compiler.
    If not ignored, innocent changes like adding a singleton within the scope of a file will lead
    to the <clinit> being marked as dirty, which then transitively marks all singletons in a given file as dirty.

    We therefore ignore such synthetic starting points for the dirty resolution.
     */
    fun ClassId.isComposableSingleton(): Boolean {
        return value.contains("ComposableSingletons\$")
    }

    fun MethodId.isIgnoredMethod(): Boolean {
        return classId.isComposableSingleton() && isClassInitializer
    }

    fun FieldId.isIgnoredField(): Boolean {
        return classId.isComposableSingleton()
    }

    fun MemberId.isIgnored(): Boolean {
        return when (this) {
            is FieldId -> this.isIgnoredField()
            is MethodId -> this.isIgnoredMethod()
        }
    }

    /* Setup initial queue */
    dirtyMethods.forEach { dirtyMethod ->
        if (dirtyMethod.methodId.isIgnoredMethod()) return@forEach
        queue.add(Element(dirtyMethod.methodId, depth = 0))
    }

    dirtyFields.forEach { dirtyField ->
        if (dirtyField.fieldId.isIgnoredField()) return@forEach
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
            if (scopeInfo.group != null && SpecialComposeGroupKeys.isRememberGroup(scopeInfo.group)) {
                /**
                 * If we do not have 'OptimizeNonSkippingGroups' enabled, then we should really mark
                 * the parent of 'remember' groups as dirty.
                 */
                return markDirty(resolveParentRuntimeScopeInfo(redefined, scopeInfo) ?: return)
            }

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
            .filter { scope -> !scope.methodId.isIgnored() }
            .forEach { scope -> markDirty(scope) }

        /* Get dependencies form redefined classes */
        redefined.dependencyIndex[element.memberId].orEmpty()
            .filter { scope -> !scope.methodId.isIgnored() }
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


    children.forEach { child ->
        if (child.group == null) return@forEach
        push(child.group.key.toLong())

        /*
        Special Case when 'OptimizeNonSkippingGroups' is not enabled:
        In this case calls to 'remember {}' will generate a separate group with a known group key.
        We do want to include such groups into the parent scope (as having them standalone will not make a lot of sense)
        Therefore, we push hash of those values into the invalidation key of this parent
         */
        if (SpecialComposeGroupKeys.isRememberGroup(child.group)) {
            push(child.hash.value)
        }
    }
    return result
}

private fun Iterable<RuntimeScopeInfo>.invalidationKey(): Long = fold(0L) { acc, scope ->
    31L * acc + scope.invalidationKey()
}

private fun RuntimeInfo.resolveParentRuntimeScopeInfo(
    redefined: RuntimeInfo, scope: RuntimeScopeInfo
): RuntimeScopeInfo? {
    val methodInfo = redefined.methodIndex[scope.methodId] ?: methodIndex[scope.methodId]
    if (methodInfo == null) {
        logger.error("'resolveParentScope' could not find method '${scope.methodId}'")
        return null
    }

    val parentScope = methodInfo.rootScope.withClosure { child -> child.children }
        .find { scope in it.children }

    if (parentScope == null) {
        logger.error("'resolveParentScope' could not find parent for '$scope'")
    }

    return parentScope
}
