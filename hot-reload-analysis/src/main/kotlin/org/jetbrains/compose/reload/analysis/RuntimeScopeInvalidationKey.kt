/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.analysis

import org.jetbrains.compose.reload.core.HotReloadEnvironment
import java.util.zip.CRC32

@JvmInline
value class RuntimeScopeInvalidationKey(val value: Long)

fun RuntimeInfo.resolveInvalidationKey(groupKey: ComposeGroupKey): RuntimeScopeInvalidationKey? {
    /*
    w/o 'OptimizeNonSkippingGroup remember {} blocks have their own dedicated group.
    This groups shall not act on their own, but are to be invalidated by their parents.
    We therefore return a stable invalidation key and later include those scopes when building
    the invalidation key for parents.
    */
    if (groupKey == SpecialComposeGroupKeys.remember) {
        return RuntimeScopeInvalidationKey(0)
    }

    val scopes = groups[groupKey] ?: return null
    return resolveInvalidationKey(scopes)
}

/**
 * Resolve an invalidation key for a method (not a Compose group).
 * This shall not be used on @Composable functions as those functions shall resolve the groups invalidation keys.
 * This might be useful for static methods
 */
fun RuntimeInfo.resolveInvalidationKey(methodId: MethodId): RuntimeScopeInvalidationKey? {
    val methodInfo = methods[methodId] ?: return null
    return resolveInvalidationKey(listOf(methodInfo.rootScope))
}

/**
 * Builds the invalidation key of the given initial [scopes] by following and resolving dependencies.
 */
private fun RuntimeInfo.resolveInvalidationKey(
    scopes: List<RuntimeScopeInfo>
): RuntimeScopeInvalidationKey {
    val crc = CRC32()
    val scopeQueue = ArrayDeque(scopes)
    val visitedScopes = hashSetOf<RuntimeScopeInfo>()
    val visitedFields = hashSetOf<FieldId>()

    while (scopeQueue.isNotEmpty()) {
        val scope = scopeQueue.removeFirst()
        if (!visitedScopes.add(scope)) continue

        crc.updateLong(scope.hash.value)

        /*
       Resolve child scopes children
        */
        scope.children.filter { info ->
            /* w/o 'OptimizeNonSkippingGroup, remember blocks will have their own group */
            SpecialComposeGroupKeys.isRememberGroup(info.tree.group ?: return@filter false)
        }.forEach { scope -> scopeQueue.addLast(scope) }

        /*
        Resolve method dependencies
         */
        scope.methodDependencies.mapNotNull { methodId -> methods[methodId] }.forEach { method ->
            /* Enqueue the dependency methods root scope */
            scopeQueue.addLast(method.rootScope)

            /* If the dependency method is open or abstract, resolve and enqueue implementations */
            if (
                HotReloadEnvironment.virtualMethodResolveEnabled &&
                (method.modality == MethodInfo.Modality.ABSTRACT || method.modality == MethodInfo.Modality.OPEN)
            ) {
                val implementationClassInfos = allImplementations[method.methodId.classId].orEmpty()

                implementationClassInfos.forEach { implementationClassInfo ->
                    implementationClassInfo.methods.values.forEach { method ->
                        if (method.methodId.methodName != method.methodId.methodName) return@forEach
                        if (method.methodId.methodDescriptor != method.methodId.methodDescriptor) return@forEach
                        scopeQueue.addLast(method.rootScope)
                    }
                }
            }
        }

        /*
        Resolve field dependencies
         */
        scope.fieldDependencies.forEach forEachField@{ fieldId ->
            if (!visitedFields.add(fieldId)) return@forEachField
            val fieldOwner = classes[fieldId.classId] ?: return@forEachField
            val field = fieldOwner.fields[fieldId] ?: return@forEachField

            when (field.initialValue) {
                null -> Unit
                is String -> crc.updateString(field.initialValue)
                is Float -> crc.updateFloat(field.initialValue)
                is Double -> crc.updateDouble(field.initialValue)
                is Int -> crc.updateInt(field.initialValue)
                is Long -> crc.updateLong(field.initialValue)
                is Boolean -> crc.updateBoolean(field.initialValue)
            }

            /*
            Static fields will resolve to the class initializer
            */
            if (field.isStatic) {
                methods[fieldOwner.classId.classInitializerMethodId]?.rootScope?.let { scope ->
                    scopeQueue.addLast(scope)
                }
            }

            /*
            Non-static fields
             */
            fieldOwner.methods.values.filter { info -> info.methodId.isConstructor }
                .forEach { method -> scopeQueue.addLast(method.rootScope) }
        }
    }

    return RuntimeScopeInvalidationKey(crc.value)
}
