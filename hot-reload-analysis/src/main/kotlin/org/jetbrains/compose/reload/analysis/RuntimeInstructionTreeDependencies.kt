/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.analysis

import org.objectweb.asm.Handle
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.InvokeDynamicInsnNode
import org.objectweb.asm.tree.MethodInsnNode

private const val lambdaMetaFactoryClassId = "java/lang/invoke/LambdaMetafactory"
private const val metafactoryMethodName = "metafactory"

internal fun RuntimeInstructionTree.methodDependencies(): Set<MethodId> {
    return tokens.filterIsInstance<RuntimeInstructionToken.BockToken>().flatMapTo(mutableSetOf()) { block ->
        block.instructions.mapNotNull { instructionNode ->
            if (instructionNode is MethodInsnNode && instructionNode.opcode == Opcodes.INVOKESTATIC) {
                if (isIgnoredClassId(instructionNode.owner)) return@mapNotNull null
                return@mapNotNull MethodId(instructionNode)
            }

            if (instructionNode is InvokeDynamicInsnNode &&
                instructionNode.bsm.owner == lambdaMetaFactoryClassId &&
                instructionNode.bsm.name == metafactoryMethodName
            ) {
                val handle = instructionNode.bsmArgs.getOrNull(1) as? Handle ?: return@mapNotNull null
                return@mapNotNull MethodId(handle)
            }

            null
        }
    }
}

internal fun RuntimeInstructionTree.fieldDependencies(): Set<FieldId> {
    return tokens
        .filterIsInstance<RuntimeInstructionToken.BockToken>()
        .flatMap { token -> token.instructions }
        .filterIsInstance<FieldInsnNode>()
        .filter { fieldInsnNode -> !isIgnoredClassId(fieldInsnNode.owner) }
        .map { fieldNode -> FieldId(fieldNode) }
        .toSet()
}
