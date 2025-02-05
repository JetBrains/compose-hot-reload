/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

@file:Suppress("DuplicatedCode")

package org.jetbrains.compose.reload.analysis

import org.jetbrains.compose.reload.core.withClosure
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode

data class RuntimeInfo(
    val classes: Map<ClassId, ClassInfo>,

    /**
     * Index from [MethodId] to the 'root scopes' belonging to this method id
     */
    val methods: Map<MethodId, List<RuntimeScopeInfo>> = classes.values.flatMap { it.methods.values }
        .groupBy { info -> info.methodId },

    /**
     * Index from [ComposeGroupKey] to *all* scopes that are associated with this group.
     * (null as key means 'no known group')
     */
    val groups: Map<ComposeGroupKey?, List<RuntimeScopeInfo>> =
        classes.values.flatMap { it.methods.values }.withClosure<RuntimeScopeInfo> { info -> info.children }
            .groupBy { info -> info.tree.group },
)

data class ClassInfo(
    val classId: ClassId,
    val fields: Map<FieldId, FieldInfo>,
    val methods: Map<MethodId, RuntimeScopeInfo>
)

data class FieldInfo(
    val fieldId: FieldId,
    val isStatic: Boolean,
    val initialValue: Any?,
)

fun ClassInfo(bytecode: ByteArray): ClassInfo? {
    return ClassInfo(ClassNode(bytecode))
}

internal fun ClassInfo(classNode: ClassNode): ClassInfo? {
    if (isIgnoredClassId(classNode.name)) return null
    val classId = ClassId(classNode)

    val methods = classNode.methods.mapNotNull { methodNode ->
        MethodId(classNode, methodNode) to (RuntimeScopeInfo(classNode, methodNode) ?: return@mapNotNull null)
    }.toMap()

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
        methods = methods
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
        tree = tree,
        methodDependencies = tree.methodDependencies(),
        fieldDependencies = tree.fieldDependencies(),
        children = tree.children.map { child -> createRuntimeScopeInfo(methodId, child) },
        hash = tree.codeHash()
    )
}
