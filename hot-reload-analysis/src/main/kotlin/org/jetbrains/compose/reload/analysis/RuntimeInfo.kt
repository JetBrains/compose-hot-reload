/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

@file:Suppress("DuplicatedCode")

package org.jetbrains.compose.reload.analysis

import org.jetbrains.compose.reload.core.closure
import org.jetbrains.compose.reload.core.withClosure
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode

data class RuntimeInfo(
    val classes: Map<ClassId, ClassInfo>,

    /**
     * Index from [MethodId] to the associated [MethodInfo]
     */
    val methods: Map<MethodId, MethodInfo> = classes.values.flatMap { it.methods.values }
        .associateBy { info -> info.methodId },

    /**
     * Index from [ComposeGroupKey] to *all* scopes that are associated with this group.
     * (null as key means 'no known group')
     */
    val groups: Map<ComposeGroupKey?, List<RuntimeScopeInfo>> =
        classes.values.flatMap { it.methods.values }
            .map { info -> info.rootScope }
            .withClosure<RuntimeScopeInfo> { info -> info.children }
            .groupBy { info -> info.group },


    /**
     * Index from a [ClassId] to its implementors (non-transitive)
     */
    val implementations: Map<ClassId, List<ClassInfo>> = run {
        val result = mutableMapOf<ClassId, MutableList<ClassInfo>>()
        classes.values.forEach { classInfo ->
            classInfo.superInterfaces.forEach { interfaceId ->
                result.getOrPut(interfaceId) { mutableListOf() }.add(classInfo)
            }

            classInfo.superClass?.let { superClass ->
                result.getOrPut(superClass) { mutableListOf() }.add(classInfo)
            }
        }

        result
    },

    val allImplementations: Map<ClassId, List<ClassInfo>> = implementations.keys.associateWith { classId ->
        classes[classId]?.closure { currentClassInfo ->
            implementations[currentClassInfo.classId].orEmpty()
        }?.toList().orEmpty()
    }
)

data class ClassInfo(
    val classId: ClassId,
    val fields: Map<FieldId, FieldInfo>,
    val methods: Map<MethodId, MethodInfo>,
    val superClass: ClassId?,
    val superInterfaces: List<ClassId>
)

data class FieldInfo(
    val fieldId: FieldId,
    val isStatic: Boolean,
    val initialValue: Any?,
)

data class MethodInfo(
    val methodId: MethodId,
    val modality: Modality,
    val rootScope: RuntimeScopeInfo,
) {
    enum class Modality {
        FINAL, OPEN, ABSTRACT
    }
}

fun ClassInfo(bytecode: ByteArray): ClassInfo? {
    return ClassInfo(ClassNode(bytecode))
}

internal fun ClassInfo(classNode: ClassNode): ClassInfo? {
    if (isIgnoredClassId(classNode.name)) return null
    val classId = ClassId(classNode)

    val methods = classNode.methods.mapNotNull { methodNode ->
        MethodInfo(
            methodId = MethodId(classNode, methodNode),
            rootScope = RuntimeScopeInfo(classNode, methodNode) ?: return@mapNotNull null,
            modality = when {
                methodNode.access and Opcodes.ACC_FINAL != 0 -> MethodInfo.Modality.FINAL
                methodNode.access and Opcodes.ACC_ABSTRACT != 0 -> MethodInfo.Modality.ABSTRACT
                else -> MethodInfo.Modality.OPEN
            }
        )
    }.associateBy { it.methodId }

    val fields = classNode.fields.associate { fieldNode ->
        FieldId(classNode, fieldNode) to FieldInfo(
            fieldId = FieldId(classNode, fieldNode),
            isStatic = fieldNode.access and (Opcodes.ACC_STATIC) != 0,
            initialValue = fieldNode.value
        )
    }

    return ClassInfo(
        classId = classId,
        fields = fields,
        methods = methods,
        superClass = classNode.superName?.let(::ClassId),
        superInterfaces = classNode.interfaces.map(::ClassId)
    )
}

internal fun RuntimeInfo(classNode: ClassNode): RuntimeInfo? {
    val classInfo = ClassInfo(classNode) ?: return null
    return RuntimeInfo(classes = mapOf(classInfo.classId to classInfo))
}

internal fun RuntimeScopeInfo(classNode: ClassNode, methodNode: MethodNode): RuntimeScopeInfo? {
    val methodId = MethodId(classNode, methodNode)
    val runtimeInstructionTree = parseRuntimeInstructionTreeLenient(methodId, methodNode)
    return createRuntimeScopeInfo(methodId, runtimeInstructionTree)
}


internal fun createRuntimeScopeInfo(
    methodId: MethodId,
    tree: RuntimeInstructionTree,
): RuntimeScopeInfo {
    return RuntimeScopeInfo(
        methodId = methodId,
        type = tree.type,
        group = tree.group,
        methodDependencies = tree.methodDependencies(),
        fieldDependencies = tree.fieldDependencies(),
        children = tree.children.map { child -> createRuntimeScopeInfo(methodId, child) },
        hash = tree.codeHash()
    )
}
