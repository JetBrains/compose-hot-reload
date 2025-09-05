/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.analysis

import org.jetbrains.compose.reload.analysis.InstructionToken.EndReplaceGroup
import org.jetbrains.compose.reload.analysis.InstructionToken.ReturnToken
import org.jetbrains.compose.reload.analysis.InstructionToken.StartReplaceGroup
import org.jetbrains.compose.reload.analysis.InstructionToken.StartRestartGroup
import org.jetbrains.compose.reload.analysis.InstructionTokenizer.TokenizerContext
import org.jetbrains.compose.reload.core.Either
import org.jetbrains.compose.reload.core.Failure
import org.jetbrains.compose.reload.core.leftOr
import org.jetbrains.compose.reload.core.nextOrNull
import org.jetbrains.compose.reload.core.toLeft
import org.jetbrains.compose.reload.core.toRight
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.JumpInsnNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.LineNumberNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.VarInsnNode

sealed class InstructionToken {
    abstract val instructions: List<AbstractInsnNode>

    data class StartRestartGroup(
        val key: ComposeGroupKey,
        override val instructions: List<AbstractInsnNode>
    ) : InstructionToken() {
        override fun toString(): String {
            return "StartRestartGroup(key=$key)"
        }
    }

    data class EndRestartGroup(
        override val instructions: List<AbstractInsnNode>
    ) : InstructionToken() {
        override fun toString(): String {
            return "EndRestartGroup()"
        }
    }

    data class StartReplaceGroup(
        val key: ComposeGroupKey,
        override val instructions: List<AbstractInsnNode>
    ) : InstructionToken() {
        override fun toString(): String {
            return "StartReplaceGroup(key=$key)"
        }
    }

    data class EndReplaceGroup(
        override val instructions: List<AbstractInsnNode>
    ) : InstructionToken() {
        override fun toString(): String {
            return "EndReplaceGroup()"
        }
    }

    data class SourceInformation(
        val sourceInformation: String,
        override val instructions: List<AbstractInsnNode>
    ) : InstructionToken() {
        override fun toString(): String {
            return "SourceInformation($sourceInformation)"
        }
    }

    data class SourceInformationMarkerStart(
        val key: ComposeGroupKey,
        val sourceInformation: String,
        override val instructions: List<AbstractInsnNode>
    ) : InstructionToken() {
        override fun toString(): String {
            return "SourceInformationMarkerStart(key=$key, sourceInformation='$sourceInformation')"
        }
    }

    data class SourceInformationMarkerEnd(
        override val instructions: List<AbstractInsnNode>
    ) : InstructionToken() {
        override fun toString(): String {
            return "SourceInformationMarkerEnd"
        }
    }

    data class JumpToken(
        val jumpInsn: JumpInsnNode
    ) : InstructionToken() {
        override val instructions = listOf(jumpInsn)
        override fun toString(): String {
            return "JumpToken(jumpInsn=${jumpInsn.opcode})"
        }
    }

    data class ReturnToken(
        val returnInsn: AbstractInsnNode
    ) : InstructionToken() {
        override val instructions = listOf(returnInsn)
        override fun toString(): String {
            return "ReturnToken(returnInsn=${returnInsn.opcode})"
        }
    }

    data class LabelToken @JvmOverloads constructor(
        val labelInsn: LabelNode,
        val lineNumberInsn: LineNumberNode? = null,
    ) : InstructionToken() {

        fun copy(labelInsn: LabelNode = this.labelInsn): LabelToken {
            return LabelToken(labelInsn, lineNumberInsn)
        }

        override val instructions: List<AbstractInsnNode> = listOfNotNull(labelInsn, lineNumberInsn)
        override fun toString(): String {
            return "LabelToken(label=${labelInsn.label}, lineNumber=${lineNumberInsn?.line})"
        }
    }

    data class CurrentMarkerToken(
        val variableIndex: Int,
        override val instructions: List<AbstractInsnNode>
    ) : InstructionToken() {
        override fun toString(): String {
            return "CurrentMarkerToken(index=$variableIndex)"
        }
    }

    data class EndToMarkerToken(
        var variableIndex: Int,
        override val instructions: List<AbstractInsnNode>
    ) : InstructionToken() {
        override fun toString(): String {
            return "EndToMarkerToken(index=$variableIndex)"
        }
    }

    data class BlockToken(
        override val instructions: List<AbstractInsnNode>
    ) : InstructionToken() {
        override fun toString(): String {
            return "BlockToken(${instructions.size})"
        }
    }
}

internal sealed class InstructionTokenizer {
    data class TokenizerContext(
        val instructions: List<AbstractInsnNode>,
        val index: Int = 0,
    ) {
        fun consumer(): Consumer = Consumer(index)

        fun skip(count: Int = 1): TokenizerContext? {
            val newIndex = index + count
            if (newIndex >= instructions.size) return null
            return TokenizerContext(instructions, newIndex)
        }

        operator fun get(i: Int) = instructions.getOrNull(index + i)

        inner class Consumer(private var nextIndex: Int) : Iterator<AbstractInsnNode> {

            inline fun <T : Any> find(search: (AbstractInsnNode) -> T?): T? {
                var value: T? = null
                while (hasNext()) {
                    val next = next()
                    value = search(next)
                    if (value != null) break
                }
                return value
            }

            /**
             * Returns all consumed nodes
             */
            fun allConsumedInstructions(): List<AbstractInsnNode> =
                instructions.subList(index, nextIndex)

            override fun next(): AbstractInsnNode {
                val element = instructions[nextIndex]
                nextIndex++
                return element
            }

            override fun hasNext(): Boolean {
                return nextIndex <= instructions.lastIndex
            }
        }
    }

    abstract fun nextToken(context: TokenizerContext): Either<InstructionToken, Failure>?
}


private class CompositeInstructionTokenizer(
    val children: List<InstructionTokenizer>
) : InstructionTokenizer() {
    constructor(vararg children: InstructionTokenizer) : this(children.toList())

    override fun nextToken(context: TokenizerContext): Either<InstructionToken, Failure>? {
        return children.firstNotNullOfOrNull { tokenizer -> tokenizer.nextToken(context) }
    }
}

private class SingleInstructionTokenizer(
    private val token: (instruction: AbstractInsnNode) -> InstructionToken?
) : InstructionTokenizer() {
    override fun nextToken(context: TokenizerContext): Either<InstructionToken, Failure>? {
        val instruction = context[0] ?: return null
        return (token(instruction) ?: return null).toLeft()
    }
}

private val priorityTokenizer by lazy {
    CompositeInstructionTokenizer(
        LabelTokenizer,
        JumpTokenizer,
        ReturnTokenizer,
        CurrentMarkerTokenizer,
        EndToMarkerTokenizer,
        StartRestartGroupTokenizer,
        EndRestartGroupTokenizer,
        StartReplaceGroupTokenizer,
        EndReplaceGroupTokenizer,
        SourceInformationTokenizer,
        SourceInformationMarkerStartTokenizer,
        SourceInformationMarkerEndTokenizer,
    )
}

private val tokenizer by lazy {
    CompositeInstructionTokenizer(
        priorityTokenizer,
        BlockTokenizer
    )
}

private object LabelTokenizer : InstructionTokenizer() {
    override fun nextToken(context: TokenizerContext): Either<InstructionToken, Failure>? {
        val labelInsn = context[0] ?: return null
        if (labelInsn !is LabelNode) return null
        val lineNumberInsn = context[1] as? LineNumberNode

        return InstructionToken.LabelToken(labelInsn, lineNumberInsn).toLeft()
    }
}

private val JumpTokenizer = SingleInstructionTokenizer { instruction ->
    if (instruction is JumpInsnNode) {
        InstructionToken.JumpToken(instruction)
    } else null
}

private val ReturnTokenizer = SingleInstructionTokenizer { instruction ->
    when (instruction.opcode) {
        Opcodes.RETURN -> ReturnToken(instruction)
        Opcodes.ARETURN -> ReturnToken(instruction)
        Opcodes.IRETURN -> ReturnToken(instruction)
        Opcodes.LRETURN -> ReturnToken(instruction)
        Opcodes.FRETURN -> ReturnToken(instruction)
        Opcodes.DRETURN -> ReturnToken(instruction)
        else -> null
    }
}

private object StartRestartGroupTokenizer : InstructionTokenizer() {
    override fun nextToken(context: TokenizerContext): Either<InstructionToken, Failure>? {
        val consumer = context.consumer()
        val expectedKeyValue = consumer.nextOrNull()?.intValueOrNull() ?: return null
        val expectedMethodInsn = consumer.nextMethodInsn() ?: return null

        if (MethodId(expectedMethodInsn) == Ids.Composer.startRestartGroup) {
            return StartRestartGroup(ComposeGroupKey(expectedKeyValue), consumer.allConsumedInstructions()).toLeft()
        }

        return null
    }
}

private object EndRestartGroupTokenizer : InstructionTokenizer() {
    override fun nextToken(context: TokenizerContext): Either<InstructionToken, Failure>? {
        val expectedMethodIns = context[0] as? MethodInsnNode ?: return null
        if (MethodId(expectedMethodIns) == Ids.Composer.endRestartGroup) {
            return InstructionToken.EndRestartGroup(listOf(expectedMethodIns)).toLeft()
        }

        return null
    }
}

private object StartReplaceGroupTokenizer : InstructionTokenizer() {
    override fun nextToken(context: TokenizerContext): Either<InstructionToken, Failure>? {
        val consumer = context.consumer()
        val expectedKeyValue = consumer.nextOrNull()?.intValueOrNull() ?: return null
        val expectedMethodInsn = consumer.nextMethodInsn() ?: return null

        if (MethodId(expectedMethodInsn) == Ids.Composer.startReplaceGroup) {
            return StartReplaceGroup(ComposeGroupKey(expectedKeyValue), consumer.allConsumedInstructions()).toLeft()
        }

        return null
    }
}

private object EndReplaceGroupTokenizer : InstructionTokenizer() {
    override fun nextToken(context: TokenizerContext): Either<InstructionToken, Failure>? {
        val expectedMethodIns = context[0] as? MethodInsnNode ?: return null
        if (MethodId(expectedMethodIns) == Ids.Composer.endReplaceGroup) {
            return EndReplaceGroup(listOf(expectedMethodIns)).toLeft()
        }

        return null
    }
}

private object SourceInformationTokenizer : InstructionTokenizer() {
    override fun nextToken(context: TokenizerContext): Either<InstructionToken, Failure>? {
        val consumer = context.consumer()

        val expectedSourceInformationLoad = consumer.nextOrNull() ?: return null
        val expectedMethodInsn = consumer.nextMethodInsn() ?: return null

        if (MethodId(expectedMethodInsn) == Ids.ComposerKt.sourceInformation) {
            val sourceInformationLdc = expectedSourceInformationLoad as? LdcInsnNode ?: return Failure(
                "Failed parsing 'sourceInformation' call: expected LDC for source information"
            ).toRight()

            return InstructionToken.SourceInformation(
                sourceInformationLdc.cst as? String ?: "N/A",
                consumer.allConsumedInstructions()
            ).toLeft()
        }

        return null
    }
}

private object SourceInformationMarkerStartTokenizer : InstructionTokenizer() {
    override fun nextToken(context: TokenizerContext): Either<InstructionToken, Failure>? {
        val consumer = context.consumer()

        /* Search for key */
        val expectedKeyLoad = consumer.nextOrNull() ?: return null
        val key = expectedKeyLoad.intValueOrNull() ?: return null

        /* search for source information */
        val sourceInformation: String = consumer.find { next ->
            when (next) {
                is LabelNode -> null
                is LineNumberNode -> null
                is LdcInsnNode -> next.cst as? String ?: "N/A"
                else -> return null
            }
        } ?: return null

        /* search for sourceInformationMarkerStart call */
        consumer.find { next ->
            when (next) {
                is LabelNode -> null
                is LineNumberNode -> null
                is MethodInsnNode -> {
                    if (MethodId(next) == Ids.ComposerKt.sourceInformationMarkerStart) {
                        return InstructionToken.SourceInformationMarkerStart(
                            key = ComposeGroupKey(key),
                            sourceInformation = sourceInformation,
                            instructions = consumer.allConsumedInstructions()
                        ).toLeft()
                    }
                }
                else -> return null
            }
        }
        return null
    }
}

private object SourceInformationMarkerEndTokenizer : InstructionTokenizer() {
    override fun nextToken(context: TokenizerContext): Either<InstructionToken, Failure>? {
        val expectedMethodInsn = context[0] ?: return null
        if (expectedMethodInsn is MethodInsnNode &&
            MethodId(expectedMethodInsn) == Ids.ComposerKt.sourceInformationMarkerEnd
        ) {
            return InstructionToken.SourceInformationMarkerEnd(
                instructions = listOf(expectedMethodInsn)
            ).toLeft()
        }
        return null
    }
}

private object CurrentMarkerTokenizer : InstructionTokenizer() {
    override fun nextToken(context: TokenizerContext): Either<InstructionToken, Failure>? {
        val expectedGetCurrentMarkerInvocation = context[0] ?: return null
        val expectedIStoreInsn = context[1] ?: return null
        if (expectedGetCurrentMarkerInvocation !is MethodInsnNode) return null
        if (MethodId(expectedGetCurrentMarkerInvocation) != Ids.Composer.getCurrentMarker) return null
        if (expectedIStoreInsn !is VarInsnNode) return null
        if (expectedIStoreInsn.opcode != Opcodes.ISTORE) return null
        return InstructionToken.CurrentMarkerToken(
            variableIndex = expectedIStoreInsn.`var`,
            instructions = listOf(expectedGetCurrentMarkerInvocation, expectedIStoreInsn)
        ).toLeft()
    }
}

private object EndToMarkerTokenizer : InstructionTokenizer() {
    override fun nextToken(context: TokenizerContext): Either<InstructionToken, Failure>? {
        val consumer = context.consumer()
        val expectedILoadInsn = consumer.nextOrNull() as? VarInsnNode ?: return null
        val expectedEndToMarkerInvocation = consumer.nextMethodInsn() ?: return null

        if (expectedILoadInsn.opcode != Opcodes.ILOAD) return null
        if (MethodId(expectedEndToMarkerInvocation) != Ids.Composer.endToMarker) return null

        return InstructionToken.EndToMarkerToken(
            variableIndex = expectedILoadInsn.`var`,
            instructions = consumer.allConsumedInstructions()
        ).toLeft()
    }
}

private object BlockTokenizer : InstructionTokenizer() {
    override fun nextToken(context: TokenizerContext): Either<InstructionToken, Failure>? {
        val instructions = mutableListOf<AbstractInsnNode>()
        var currentContext = context
        while (true) {
            if (priorityTokenizer.nextToken(currentContext) != null) break
            instructions.add(currentContext[0] ?: break)
            currentContext = currentContext.skip(1) ?: break
        }
        if (instructions.isEmpty()) return null
        return InstructionToken.BlockToken(instructions).toLeft()
    }
}

internal fun tokenizeInstructions(
    instructions: List<AbstractInsnNode>
): Either<List<InstructionToken>, Failure> {
    if (instructions.isEmpty()) return Failure("Empty list of instructions").toRight()

    var context = TokenizerContext(instructions)
    val tokens = mutableListOf<InstructionToken>()

    while (true) {
        val nextResult = tokenizer.nextToken(context) ?: return Failure("Cannot build next token").toRight()
        val nextToken = nextResult.leftOr { failure -> return failure }
        tokens.add(nextToken)
        context = context.skip(nextToken.instructions.size) ?: break
    }

    return tokens.toLeft()
}

/**
 * Consumes [LabelNode] and [LineNumberNode] nodes until a [MethodInsnNode] is found.
 * If any other, semantically important node is encountered, the method returns `null`.
 */
private fun TokenizerContext.Consumer.nextMethodInsn(): MethodInsnNode? {
    return find { node ->
        when (node) {
            is MethodInsnNode -> node
            is LabelNode, is LineNumberNode -> null
            else -> return null
        }
    }
}
