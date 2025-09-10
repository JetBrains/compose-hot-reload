/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.analysis

import org.jetbrains.compose.reload.InternalHotReloadApi
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode

@ConsistentCopyVisibility
data class ClassInfo internal constructor(
    val classId: ClassId,
    val fields: Map<FieldId, FieldInfo>,
    val methods: Map<MethodId, MethodInfo>,
    val superClass: ClassId?,
    val superInterfaces: List<ClassId>,
    val flags: ClassFlags,
    val sourceFile: String?,
)

fun ClassInfo(bytecode: ByteArray): ClassInfo? {
    return ClassInfo(ClassNode(bytecode))
}

@InternalHotReloadApi
fun ClassInfo(classNode: ClassNode): ClassInfo? {
    val classId = ClassId(classNode)
    if (classId.isIgnored) return null

    val methods = classNode.methods.mapNotNull { methodNode ->
        MethodInfo(
            methodId = MethodId(classNode, methodNode),
            methodType = MethodType(methodNode),
            rootScope = ScopeInfo(classNode, methodNode),
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
            initialValue = fieldNode.value,
            additionalChangeIndicatorHash = getResourceContentHash(fieldNode)
        )
    }

    return ClassInfo(
        classId = classId,
        fields = fields,
        methods = methods,
        superClass = classNode.superName?.let(::ClassId),
        superInterfaces = classNode.interfaces.map(::ClassId),
        flags = ClassFlags(classNode.access),
        sourceFile = classNode.sourceFile,
    )
}

private fun getResourceContentHash(fieldNode: FieldNode?): Int? {
    try {
        return fieldNode?.invisibleAnnotations?.find { isResourceContentHashAnnotation(it) }?.values[1] as Int?
    } catch (_: Throwable) {
        return null
    }
}

private fun isResourceContentHashAnnotation(it: AnnotationNode): Boolean = it.desc == Ids.ResourceContentHash.classId.descriptor
