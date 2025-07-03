/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.analysis

import org.jetbrains.compose.reload.core.Context
import org.jetbrains.compose.reload.core.HotReloadEnvironment
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.error
import org.jetbrains.compose.reload.core.info
import org.jetbrains.compose.reload.core.simpleName
import org.jetbrains.compose.reload.core.withClosure
import java.util.ServiceLoader
import kotlin.time.measureTimedValue

private val logger = createLogger()

data class RuntimeDirtyScopes(
    val redefinedClasses: List<ClassInfo>,
    val dirtyScopes: List<RuntimeScopeInfo>
) {
    val dirtyMethodIds = dirtyScopes.groupBy { it.methodId }
}

fun Context.resolveDirtyRuntimeScopes(current: RuntimeInfo, redefined: RuntimeInfo): RuntimeDirtyScopes {
    val (redefinition, duration) = measureTimedValue {
        RuntimeDirtyScopes(
            redefinedClasses = redefined.classIndex.values.toList(),
            dirtyScopes = resolveDirtyRuntimeScopeInfos(current, redefined)
        )
    }

    logger.info("${simpleName<RuntimeDirtyScopes>()} resolved in [${duration}]")
    return redefinition
}

private fun Context.resolveDirtyRuntimeScopeInfos(
    current: RuntimeInfo, redefined: RuntimeInfo
): List<RuntimeScopeInfo> {
    val dirtyComposeScopes = resolveDirtyComposeScopes(current, redefined) +
        resolveRemovedComposeScopes(current, redefined)

    val dirtyMethods = resolveDirtyMethodsFromExtensionPoints(current, redefined) +
        resolveDirtyMethods(current, redefined) +
        resolveRemovedMethods(current, redefined)

    val dirtyFields = resolveDirtyFields(current, redefined) +
        resolveRemovedFields(current, redefined)

    val transitivelyDirty = resolveTransitivelyDirty(current, redefined, dirtyMethods, dirtyFields)

    return buildSet {
        addAll(dirtyMethods.map { it.rootScope })
        addAll(dirtyComposeScopes)
        addAll(transitivelyDirty)
    }.toList()
}

private fun resolveDirtyMethods(current: RuntimeInfo, redefined: RuntimeInfo): List<MethodInfo> {
    return redefined.methodIndex.mapNotNull { (methodId, redefinedMethod) ->
        val previousMethod = current.methodIndex[methodId] ?: return@mapNotNull redefinedMethod
        if (previousMethod.rootScope.hash != redefinedMethod.rootScope.hash) {
            return@mapNotNull redefinedMethod
        }
        if (previousMethod.rootScope.children.map { it.group } != redefinedMethod.rootScope.children.map { it.group }) {
            return@mapNotNull redefinedMethod
        }
        null
    }
}

private fun resolveRemovedMethods(current: RuntimeInfo, redefined: RuntimeInfo): List<MethodInfo> {
    return redefined.classIndex.flatMap { (classId, redefinedClass) ->
        val previousClass = current.classIndex[classId] ?: return@flatMap emptyList()
        previousClass.methods.mapNotNull { (methodId, method) ->
            if (methodId in redefinedClass.methods) return@mapNotNull null
            else method
        }
    }
}

private fun resolveDirtyFields(current: RuntimeInfo, redefined: RuntimeInfo): List<FieldInfo> {
    return redefined.fieldIndex.mapNotNull { (fieldId, redefinedField) ->
        val previousField = current.fieldIndex[fieldId] ?: return@mapNotNull redefinedField
        if (previousField.initialValue != redefinedField.initialValue) {
            return@mapNotNull redefinedField
        }
        null
    }
}

private fun resolveRemovedFields(current: RuntimeInfo, redefined: RuntimeInfo): List<FieldInfo> {
    return redefined.classIndex.flatMap { (classId, redefinedClass) ->
        val previousClass = current.classIndex[classId] ?: return@flatMap emptyList()
        previousClass.fields.mapNotNull { (fieldId, field) ->
            if (fieldId in redefinedClass.fields) return@mapNotNull null
            else field
        }
    }
}

private fun resolveDirtyComposeScopes(current: RuntimeInfo, redefined: RuntimeInfo): List<RuntimeScopeInfo> {
    val result = mutableListOf<RuntimeScopeInfo>()
    redefined.groupIndex.forEach forEachGroup@{ (groupKey, redefinedGroup) ->
        if (groupKey == null) return@forEachGroup
        if (SpecialComposeGroupKeys.isRememberGroup(groupKey)) return@forEachGroup

        current.groupIndex[groupKey]?.let { originalGroup ->
            val originalGroupInvalidationKey = originalGroup.invalidationKey()
            val redefinedGroupInvalidationKey = redefinedGroup.invalidationKey()
            if (originalGroupInvalidationKey != redefinedGroupInvalidationKey) {
                result.addAll(redefinedGroup)
            }
        }
    }
    return result
}

private fun resolveRemovedComposeScopes(current: RuntimeInfo, redefined: RuntimeInfo): List<RuntimeScopeInfo> {
    val result = mutableListOf<RuntimeScopeInfo>()
    redefined.methodIndex.forEach forEachMethod@{ (methodId, redefinedMethod) ->
        val previousMethod = current.methodIndex[methodId] ?: return@forEachMethod
        val redefinedScopeGroups = redefinedMethod.allScopes.map { it.group }
        previousMethod.allScopes.forEach { previousScope ->
            if (previousScope.group !in redefinedScopeGroups) result.add(previousScope)
        }
    }
    return result
}

private fun Context.resolveDirtyMethodsFromExtensionPoints(
    current: RuntimeInfo, redefined: RuntimeInfo
): List<MethodInfo> {
    return ServiceLoader.load(DirtyResolver::class.java, ClassLoader.getSystemClassLoader()).flatMap { resolver ->
        resolver.resolveDirtyMethods(this, current, redefined)
    }
}

/**
 * This method will start at the [dirtyFields] and [dirtyMethods] provided by it and
 * walks the dependency graph in [RuntimeInfo] (e.g. [RuntimeInfo.dependencyIndex] or [RuntimeInfo.superIndex]).
 * Visited elements therefore depend on dirty scopes and are therefore also marked as dirty.
 *
 * This algorithm is configured to only search the graph using a maximum depth
 * (configured by [org.jetbrains.compose.reload.core.HotReloadProperty.DirtyResolveDepthLimit]).
 */
private fun resolveTransitivelyDirty(
    current: RuntimeInfo,
    redefined: RuntimeInfo,
    dirtyMethods: List<MethodInfo>,
    dirtyFields: List<FieldInfo>,
): List<RuntimeScopeInfo> {

    class Element(val memberId: MemberId, val depth: Int, val scope: RuntimeScopeInfo? = null)

    val queue = ArrayDeque<Element>()
    val visited = hashMapOf<MemberId, Element>()
    val transitivelyDirtyScopes = mutableListOf<RuntimeScopeInfo>()

    fun ClassId.isComposableSingleton(): Boolean {
        return value.contains("ComposableSingletons\$")
    }

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
        val previousElement = visited.put(element.memberId, element)

        /*
        ################################################################
         Phase 1:
         Check if this element is ignored before marking it as dirty!
        ################################################################
         */

        /* If we have seen this member already (with higher or equal depth), then we skip it */
        if (previousElement != null && previousElement.depth <= element.depth) continue

        /**
         * The dirty resolution typically starts with known dirty methods or fields.
         * Those 'starting points' will be followed to mark dependent code as dirty as well (transitively).
         * Some special classes, or some special code, however, can be ignored as a starting point:
         * e.g., `ComposableSingletons` are considered an intermediate hub generated by the compiler.
         * If not ignored, innocent changes like adding a singleton within the scope of a file will lead
         * to the <clinit> being marked as dirty, which then transitively marks all singletons in a given file as dirty.
         *
         * We therefore ignore such synthetic starting points for the dirty resolution.
         */
        if (element.memberId.classId.isComposableSingleton()) {
            when (val id = element.memberId) {
                is FieldId -> continue
                is MethodId -> if (id.isClassInitializer) continue
            }
        }

        /*
        ################################################################
         Phase 2:
         We passed initial checks, and we can mark the scope as dirty!
        ################################################################
         */
        if (element.scope != null) {
            transitivelyDirtyScopes.add(element.scope)
        }

        /*
        ################################################################
        Phase 3:
        We check if the current element is allowed to transitively resolve further elements as dirty:
        Some elements act as 'boundaries': e.g., a Composable function returning Unit will
        not mark other Composable functions, which depend on it, as dirty,

        There is also a limit on how deep we allow the resolution of transitive elements.
        ################################################################
         */

        /*
        This element gets rejected: We reached our depth limit
         */
        if (element.depth > HotReloadEnvironment.dirtyResolveDepthLimit) {
            continue
        }

        /*
        Composable functions act as boundaries for "transitive dirty resolution": e.g.
        ```kotlin
        @Composable
        fun Foo() {
           Bar()
        }

        @Composable
        fun Bar() {
            // ...
        }
        ```

        Foo might depend on Bar, but when 'Bar' is marked as dirty, it is enough to mark 'Bar' as Dirty and
        keep the state of 'Foo'
         */
        val currentMethod = current.methodIndex[element.memberId]
        if (currentMethod != null && currentMethod.rootScope.group != null) {
            continue
        }

        /*
        ################################################################
        Phase 4:
        We resolve elements which are supposed to get marked dirty as well.,
        ################################################################
        */

        /*
        Adds the provided [scope] to the current queue
         */
        fun enqueue(scope: RuntimeScopeInfo) {
            if (scope.group != null && SpecialComposeGroupKeys.isRememberGroup(scope.group)) {
                /**
                 * If we do not have 'OptimizeNonSkippingGroups' enabled, then we should really mark
                 * the parent of 'remember' groups as dirty.
                 */
                return enqueue(current.resolveParentRuntimeScopeInfo(redefined, scope) ?: return)
            }

            queue.add(Element(scope.methodId, depth = element.depth + 1, scope = scope))
        }

        /* Get dependencies from current runtime */
        current.dependencyIndex[element.memberId].orEmpty()
            .filter { scope -> scope.methodId.classId !in redefined.classIndex }
            .forEach { scope -> enqueue(scope) }

        /* Get dependencies form redefined classes */
        redefined.dependencyIndex[element.memberId].orEmpty()
            .forEach { scope -> enqueue(scope) }

        /* Dirty classInitializer -> dirty static fields */
        if (element.memberId is MethodId && element.memberId.isClassInitializer) {
            val classInfo = redefined.classIndex[element.memberId.classId]
                ?: current.classIndex[element.memberId.classId]

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
            else current.superIndex[classId]

            superClasses?.forEach { superClassId ->
                val superMethodDescriptor = element.memberId.copy(superClassId)
                queue.add(Element(superMethodDescriptor, depth = element.depth + 1))
            }
        }
    }

    return transitivelyDirtyScopes
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

    val parentScope = methodInfo.rootScope
        .withClosure { child -> child.children }
        .find { scope in it.children }

    if (parentScope == null) {
        logger.error("'resolveParentScope' could not find parent for '$scope'")
    }

    return parentScope
}
