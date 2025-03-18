/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.analysis

import org.jetbrains.compose.reload.core.withClosure

interface TrackingRuntimeInfo : RuntimeInfo {
    fun add(info: ClassInfo)
    fun remove(classId: ClassId)
    fun copy(): TrackingRuntimeInfo
    fun clear()
}

fun TrackingRuntimeInfo(): TrackingRuntimeInfo = TrackingRuntimeInfoImpl()

private class TrackingRuntimeInfoImpl(
    override val classIndex: MutableMap<ClassId, ClassInfo> = mutableMapOf(),
    override val methodIndex: MutableMap<MethodId, MethodInfo> = mutableMapOf(),
    override val fieldIndex: MutableMap<FieldId, FieldInfo> = mutableMapOf(),
    override val groupIndex: MutableMap<ComposeGroupKey?, MutableSet<RuntimeScopeInfo>> = mutableMapOf(),
    override val superIndex: MutableMap<ClassId, MutableSet<ClassId>> = mutableMapOf(),
    override val superIndexInverse: MutableMap<ClassId, MutableSet<ClassId>> = mutableMapOf(),
    override val dependencyIndex: MutableMap<MemberId, MutableSet<RuntimeScopeInfo>> = mutableMapOf()
) : TrackingRuntimeInfo {

    override fun copy(): TrackingRuntimeInfo {
        return TrackingRuntimeInfoImpl(
            classIndex = classIndex.toMutableMap(),
            methodIndex = methodIndex.toMutableMap(),
            fieldIndex = fieldIndex.toMutableMap(),
            groupIndex = groupIndex.toMutableMap(),
            superIndex = superIndex.toMutableMap(),
            superIndexInverse = superIndexInverse.toMutableMap(),
            dependencyIndex = dependencyIndex.toMutableMap(),
        )
    }

    override fun clear() {
        classIndex.clear()
        methodIndex.clear()
        fieldIndex.clear()
        groupIndex.clear()
        superIndex.clear()
        superIndexInverse.clear()
        dependencyIndex.clear()
    }

    override fun add(info: ClassInfo) {
        classIndex[info.classId] = info

        methodIndex.putAll(info.methods)
        fieldIndex.putAll(info.fields)

        val allScopes = info.methods.values.map { it.rootScope }
            .withClosure<RuntimeScopeInfo> { scope -> scope.children }

        /* Fill groupIndex */
        allScopes.forEach { scope ->
            groupIndex.getOrPut(scope.group) { mutableSetOf() }.add(scope)
        }

        /* Fill superIndex & superIndexInverse */
        val superClassifiers = listOfNotNull(info.superClass, *info.superInterfaces.toTypedArray()).toMutableSet()
        superIndex[info.classId] = superClassifiers
        superClassifiers.forEach { superClassifier ->
            superIndexInverse.getOrPut(superClassifier) { mutableSetOf() }.add(info.classId)
        }

        /* Fill dependency index */
        allScopes.forEach { scope ->
            scope.methodDependencies.forEach { methodId ->
                dependencyIndex.getOrPut(methodId) { mutableSetOf() }.add(scope)
            }

            scope.fieldDependencies.forEach { fieldId ->
                dependencyIndex.getOrPut(fieldId) { mutableSetOf() }.add(scope)
            }
        }
    }

    override fun remove(classId: ClassId) {
        val previousClassInfo = classIndex.remove(classId) ?: return

        previousClassInfo.methods.forEach { methodInfo ->
            methodIndex.remove(methodInfo.key)
        }

        previousClassInfo.fields.forEach { fieldInfo ->
            fieldIndex.remove(fieldInfo.key)
        }

        val previousAllScopes = previousClassInfo.methods.values.map { it.rootScope }
            .withClosure<RuntimeScopeInfo> { scope -> scope.children }

        previousAllScopes.forEach { scope ->
            groupIndex[scope.group]?.apply {
                remove(scope)
                if (isEmpty()) groupIndex.remove(scope.group)
            }

            scope.methodDependencies.forEach { methodId ->
                dependencyIndex[methodId]?.apply {
                    remove(scope)
                    if (isEmpty()) dependencyIndex.remove(methodId)
                }
            }

            scope.fieldDependencies.forEach { fieldId ->
                dependencyIndex[fieldId]?.apply {
                    remove(scope)
                    if (isEmpty()) dependencyIndex.remove(fieldId)
                }
            }
        }

        val previousSuperClassifiers = listOfNotNull(
            previousClassInfo.superClass,
            *previousClassInfo.superInterfaces.toTypedArray()
        )

        superIndex.remove(classId)
        previousSuperClassifiers.forEach { superClassifier ->
            superIndexInverse[superClassifier]?.apply {
                remove(classId)
                if (isEmpty()) superIndexInverse.remove(superClassifier)
            }
        }
    }

    override fun hashCode(): Int {
        return 27 * classIndex.hashCode() +
            31 * methodIndex.hashCode() +
            31 * fieldIndex.hashCode() +
            31 * groupIndex.hashCode() +
            31 * superIndex.hashCode() +
            31 * superIndexInverse.hashCode() +
            31 * dependencyIndex.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RuntimeInfo) return false
        if (classIndex != other.classIndex) return false
        if (methodIndex != other.methodIndex) return false
        if (fieldIndex != other.fieldIndex) return false
        if (groupIndex != other.groupIndex) return false
        if (superIndex != other.superIndex) return false
        if (superIndexInverse != other.superIndexInverse) return false
        if (dependencyIndex != other.dependencyIndex) return false
        return true
    }
}
