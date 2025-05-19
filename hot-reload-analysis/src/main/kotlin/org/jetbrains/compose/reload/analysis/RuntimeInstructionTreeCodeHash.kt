/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.analysis

import org.jetbrains.compose.reload.analysis.Ids.ComposerKt.traceEventStart
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.IntInsnNode
import org.objectweb.asm.tree.InvokeDynamicInsnNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.VarInsnNode
import java.util.zip.CRC32
import kotlin.collections.orEmpty

@JvmInline
value class RuntimeInstructionTreeCodeHash(val value: Long)

internal fun RuntimeInstructionTree.codeHash(methodNode: MethodNode): RuntimeInstructionTreeCodeHash {
    val crc = CRCHasher()

    tokens.forEach token@{ token ->
        if (token is RuntimeInstructionToken.SourceInformation ||
            token is RuntimeInstructionToken.SourceInformationMarkerStart ||
            token is RuntimeInstructionToken.SourceInformationMarkerEnd
        ) {
            return@token
        }

        token.instructions.forEach instruction@{ instructionNode ->
            if (instructionNode.opcode > 0) {
                crc.pushHash(instructionNode.opcode)
            }

            when (instructionNode) {
                is MethodInsnNode -> {
                    crc.pushHash(instructionNode.owner)
                    crc.pushHash(instructionNode.name)
                    crc.pushHash(instructionNode.desc)
                    crc.pushHash(instructionNode.itf)
                }

                is LdcInsnNode -> {
                    val nextInstruction = instructionNode.next

                    /*
                    We want to ignore constants pushed into 'traceEventStart' as this traces will include
                    line numbers. Such calls also don't contribute to implementation of the method.
                     */
                    if (nextInstruction is MethodInsnNode && MethodId(nextInstruction) == traceEventStart) {
                        return@instruction
                    }

                    crc.pushHash(instructionNode.cst)
                }

                is InvokeDynamicInsnNode -> {
                    crc.pushHash(instructionNode.name)
                    crc.pushHash(instructionNode.desc)
                    crc.pushHash(instructionNode.bsm?.name)
                    crc.pushHash(instructionNode.bsm?.owner)
                    crc.pushHash(instructionNode.bsm?.tag)
                    crc.pushHash(instructionNode.bsm?.desc)
                    instructionNode.bsmArgs.forEach { crc.pushHash(it) }
                }

                is FieldInsnNode -> {
                    crc.pushHash(instructionNode.owner)
                    crc.pushHash(instructionNode.name)
                    crc.pushHash(instructionNode.desc)
                }

                is IntInsnNode -> {
                    crc.pushHash(instructionNode.operand)
                }

                is VarInsnNode -> {
                    crc.pushHash(instructionNode.`var`)
                }
            }
        }
    }

    /*
    This is experimental/defensive:
    Let's also incorporate the structure of children to the hash
     */
    children.forEach { child ->
        crc.pushHash(child.type.name)
        crc.pushHash(child.group?.key)
        crc.pushHash(child.failure?.message)
        crc.pushHash(child.failure?.throwable?.stackTraceToString())
    }

    /**
     * We need to store the information about local variables
     */
    methodNode.localVariables.orEmpty().forEach { localVariable ->
        crc.pushHash(localVariable.name)
        crc.pushHash(localVariable.desc)
    }

    return RuntimeInstructionTreeCodeHash(crc.value)
}

internal class CRCHasher() {
    private val crc = CRC32()
    val value: Long get() = crc.value

    fun pushHash(value: Any?) {
        if (value == null) return
        when (value) {
            is Boolean -> crc.update(if (value) 1 else 0)
            is String -> crc.update(value.toByteArray())
            is Byte -> crc.update(value.toInt())
            is Int -> crc.updateInt(value)
            is Long -> crc.updateLong(value)
            is Float -> crc.updateFloat(value)
            is Double -> crc.updateDouble(value)
            else -> crc.update(value.toString().toByteArray())
        }
    }
}
