/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.analysis

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassReader.SKIP_CODE
import org.objectweb.asm.ClassReader.SKIP_FRAMES
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Handle
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Opcodes.ASM9
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode

internal fun AbstractInsnNode.intValueOrNull(): Int? {
    if (this is LdcInsnNode) return this.cst as? Int
    return when (opcode) {
        Opcodes.ICONST_0 -> 0
        Opcodes.ICONST_1 -> 1
        Opcodes.ICONST_2 -> 2
        Opcodes.ICONST_3 -> 3
        Opcodes.ICONST_4 -> 4
        Opcodes.ICONST_5 -> 5
        Opcodes.ICONST_M1 -> -1
        else -> null
    }
}

internal fun MethodNode.readFunctionKeyMetaAnnotation(): ComposeGroupKey? {
    val functionKey = visibleAnnotations.orEmpty().find { annotationNode ->
        annotationNode.desc == Ids.FunctionKeyMeta.classId.descriptor
    }?.values?.zipWithNext()?.find { (name, _) -> name == "key" }?.second as? Int

    if (functionKey != null) {
        return ComposeGroupKey(functionKey)
    }

    return null
}

internal fun ClassNode(bytecode: ByteArray): ClassNode {
    val reader = ClassReader(bytecode)
    val node = ClassNode(ASM9)
    reader.accept(node, 0)
    return node
}

fun ClassId(bytecode: ByteArray): ClassId? {
    var className: String? = null
    val reader = ClassReader(bytecode)
    reader.accept(object : ClassVisitor(ASM9) {
        override fun visit(
            version: Int, access: Int, name: String?,
            signature: String?, superName: String?, interfaces: Array<out String?>?
        ) {
            className = name
        }
    }, SKIP_CODE and SKIP_FRAMES and ClassReader.SKIP_DEBUG)

    return className?.let { name -> ClassId(name) }
}

internal fun ClassId(node: ClassNode): ClassId = ClassId(node.name)

internal fun MethodId(classNode: ClassNode, methodNode: MethodNode): MethodId = MethodId(
    classId = ClassId(classNode),
    methodName = methodNode.name,
    methodDescriptor = methodNode.desc,
)

internal fun MethodId(handle: Handle) = MethodId(
    classId = ClassId(handle.owner),
    methodName = handle.name,
    methodDescriptor = handle.desc
)

internal fun MethodId(methodInsnNode: MethodInsnNode): MethodId = MethodId(
    classId = ClassId(methodInsnNode.owner),
    methodName = methodInsnNode.name,
    methodDescriptor = methodInsnNode.desc
)

internal fun FieldId(fieldIsnNode: FieldInsnNode): FieldId = FieldId(
    classId = ClassId(fieldIsnNode.owner),
    fieldName = fieldIsnNode.name,
    fieldDescriptor = fieldIsnNode.desc
)

internal fun FieldId(classNode: ClassNode, fieldNode: FieldNode): FieldId = FieldId(
    classId = ClassId(classNode),
    fieldName = fieldNode.name,
    fieldDescriptor = fieldNode.desc
)
