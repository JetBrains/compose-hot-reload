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
import org.objectweb.asm.tree.VarInsnNode
import java.util.zip.CRC32

@JvmInline
value class RuntimeInstructionTreeCodeHash(val value: Long)

internal fun RuntimeInstructionTree.codeHash(): RuntimeInstructionTreeCodeHash {
    val crc = CRC32()

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


    tokens.forEach token@{ token ->
        if (token is RuntimeInstructionToken.SourceInformation ||
            token is RuntimeInstructionToken.SourceInformationMarkerStart ||
            token is RuntimeInstructionToken.SourceInformationMarkerEnd
        ) {
            return@token
        }

        token.instructions.forEach instruction@{ instructionNode ->
            if (instructionNode.opcode > 0) {
                pushHash(instructionNode.opcode)
            }

            when (instructionNode) {
                is MethodInsnNode -> {
                    pushHash(instructionNode.owner)
                    pushHash(instructionNode.name)
                    pushHash(instructionNode.desc)
                    pushHash(instructionNode.itf)
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

                    pushHash(instructionNode.cst)
                }

                is InvokeDynamicInsnNode -> {
                    pushHash(instructionNode.name)
                    pushHash(instructionNode.desc)
                    pushHash(instructionNode.bsm?.name)
                    pushHash(instructionNode.bsm?.owner)
                    pushHash(instructionNode.bsm?.tag)
                    pushHash(instructionNode.bsm?.desc)
                    instructionNode.bsmArgs.forEach { pushHash(it) }
                }

                is FieldInsnNode -> {
                    pushHash(instructionNode.owner)
                    pushHash(instructionNode.name)
                    pushHash(instructionNode.desc)
                }

                is IntInsnNode -> {
                    pushHash(instructionNode.operand)
                }

                is VarInsnNode -> {
                    pushHash(instructionNode.`var`)
                }
            }
        }
    }

    /*
    This is experimental/defensive:
    Let's also incorporate the structure of children to the hash
     */
    children.forEach { child ->
        pushHash(child.type.name)
        pushHash(child.group?.key)
        pushHash(child.failure?.message)
        pushHash(child.failure?.throwable?.stackTraceToString())
    }

    return RuntimeInstructionTreeCodeHash(crc.value)
}
