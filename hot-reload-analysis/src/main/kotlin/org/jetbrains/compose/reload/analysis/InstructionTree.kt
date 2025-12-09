/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.analysis

import org.jetbrains.compose.reload.analysis.InstructionToken.BlockToken
import org.jetbrains.compose.reload.analysis.InstructionToken.CurrentMarkerToken
import org.jetbrains.compose.reload.analysis.InstructionToken.EndReplaceGroup
import org.jetbrains.compose.reload.analysis.InstructionToken.EndRestartGroup
import org.jetbrains.compose.reload.analysis.InstructionToken.EndToMarkerToken
import org.jetbrains.compose.reload.analysis.InstructionToken.JumpToken
import org.jetbrains.compose.reload.analysis.InstructionToken.LabelToken
import org.jetbrains.compose.reload.analysis.InstructionToken.ReturnToken
import org.jetbrains.compose.reload.analysis.InstructionToken.SourceInformation
import org.jetbrains.compose.reload.analysis.InstructionToken.SourceInformationMarkerEnd
import org.jetbrains.compose.reload.analysis.InstructionToken.SourceInformationMarkerStart
import org.jetbrains.compose.reload.analysis.InstructionToken.StartReplaceGroup
import org.jetbrains.compose.reload.analysis.InstructionToken.StartRestartGroup
import org.jetbrains.compose.reload.analysis.InstructionToken.SwitchToken
import org.jetbrains.compose.reload.core.Either
import org.jetbrains.compose.reload.core.Failure
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.leftOr
import org.jetbrains.compose.reload.core.toLeft
import org.jetbrains.compose.reload.core.toRight
import org.jetbrains.compose.reload.core.warn
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.MethodNode

private val logger = createLogger()

@ConsistentCopyVisibility
data class InstructionTree internal constructor(
    val group: ComposeGroupKey?,
    val type: ScopeType,
    val tokens: List<InstructionToken>,
    val children: List<InstructionTree>,

    /**
     * Indicating that this tree was built with some failure. Using this tree requires caution!
     */
    val failure: Failure? = null,
)

internal fun parseInstructionTreeLenient(methodId: MethodId, methodNode: MethodNode): InstructionTree {
    /* Handle methods w/o bodies */
    if (methodNode.instructions.size() == 0) {
        return InstructionTree(
            group = methodNode.readFunctionKeyMetaAnnotation(), type = ScopeType.Method,
            tokens = emptyList(), children = emptyList()
        )
    }

    val tokens = tokenizeInstructions(methodNode.instructions.toList()).leftOr { right ->
        /* Fallback for methods that even fail to tokenize */
        logger.warn("'tokenizeInstructions' failed on $methodId: $right")
        val tokens = listOf(BlockToken(methodNode.instructions.toList()))
        return InstructionTree(
            group = methodNode.readFunctionKeyMetaAnnotation(),
            type = ScopeType.Method,
            tokens = tokens,
            children = emptyList(),
            failure = right.value
        )
    }

    return parseInstructionTree(methodNode, tokens).leftOr { right ->
        logger.warn("'parseInstructionTree' failed on $methodId: $right")
        return InstructionTree(
            group = methodNode.readFunctionKeyMetaAnnotation(),
            type = ScopeType.Method,
            tokens = tokens,
            children = emptyList(),
            failure = right.value
        )
    }
}

internal fun parseInstructionTree(methodNode: MethodNode): Either<InstructionTree, Failure> {
    return parseInstructionTree(
        methodNode, tokenizeInstructions(methodNode.instructions.toList()).leftOr { return it }
    )
}

internal fun parseInstructionTree(
    methodNode: MethodNode, tokens: List<InstructionToken>
): Either<InstructionTree, Failure> {
    return linearParseInstructionTree(methodNode, tokens)
}

private class MutableTree(
    var group: ComposeGroupKey?,
    var type: ScopeType,
    val tokens: MutableList<InstructionToken>,
    val children: MutableList<MutableTree>,
    var parent: MutableTree? = null,
    val markers: MutableSet<Int> = mutableSetOf()
) {
    fun toInstructionTree(): InstructionTree = InstructionTree(
        group = this.group,
        type = this.type,
        tokens = this.tokens,
        children = this.children.map { it.toInstructionTree() },
    )
}

private fun MutableList<MutableTree?>.matchToken2Tree(
    tokenIndex: Int,
    tree: MutableTree?
) {
    if (this[tokenIndex] == null) {
        this[tokenIndex] = tree
    }
}

private fun linearParseInstructionTree(
    methodNode: MethodNode, tokens: List<InstructionToken>
): Either<InstructionTree, Failure> {
    if (tokens.isEmpty()) return Failure("empty tokens").toRight()

    val labels = tokens.withIndex().filter { it.value is LabelToken }.associate {
        (it.value as LabelToken).labelInsn.label to it.index
    }
    val token2Tree = MutableList<MutableTree?>(tokens.size + 1) { null }
    val root = MutableTree(
        group = methodNode.readFunctionKeyMetaAnnotation(),
        type = ScopeType.Method,
        tokens = mutableListOf(),
        children = mutableListOf(),
    )
    token2Tree.matchToken2Tree(0, root)

    for ((currentIndex, currentToken) in tokens.withIndex()) {
        val currentNode = token2Tree[currentIndex] ?: continue
        val nextIndex = currentIndex + 1
        when (currentToken) {
            is BlockToken, is LabelToken, is SourceInformation, is SourceInformationMarkerStart, is SourceInformationMarkerEnd -> {
                currentNode.tokens += currentToken
                token2Tree.matchToken2Tree(nextIndex, currentNode)
            }

            is StartRestartGroup -> {
                val newNode = MutableTree(
                    group = currentToken.key,
                    type = ScopeType.RestartGroup,
                    tokens = mutableListOf(),
                    children = mutableListOf(),
                    parent = currentNode,
                )
                token2Tree[currentIndex] = newNode

                currentNode.children += newNode
                newNode.tokens += currentToken
                token2Tree.matchToken2Tree(nextIndex, newNode)
            }

            is EndRestartGroup -> {
                if (currentNode.type != ScopeType.RestartGroup) {
                    return Failure("EndRestartGroup is not allowed in ${currentNode.type} scope").toRight()
                }
                currentNode.tokens += currentToken
                token2Tree.matchToken2Tree(nextIndex, currentNode.parent)
            }

            is StartReplaceGroup -> {
                val newNode = MutableTree(
                    group = currentToken.key,
                    type = ScopeType.ReplaceGroup,
                    tokens = mutableListOf(),
                    children = mutableListOf(),
                    parent = currentNode
                )
                token2Tree[currentIndex] = newNode

                currentNode.children += newNode
                newNode.tokens += currentToken
                token2Tree.matchToken2Tree(nextIndex, newNode)
            }

            is EndReplaceGroup -> {
                if (currentNode.type != ScopeType.ReplaceGroup) {
                    return Failure("EndReplaceGroup is not allowed in ${currentNode.type} scope").toRight()
                }
                currentNode.tokens += currentToken
                token2Tree.matchToken2Tree(nextIndex, currentNode.parent)
            }

            is JumpToken -> {
                currentNode.tokens += currentToken

                val jumpIndex = labels[currentToken.jumpInsn.label.label]
                    ?: return Failure("Jump target label ${currentToken.jumpInsn.label.label} not found").toRight()

                /* Perform the forward jump; We don't care about backward jumps, because they are already handled */
                if (jumpIndex > currentIndex) {
                    token2Tree.matchToken2Tree(jumpIndex, currentNode)
                }

                /* Continue execution on path w/o jump */
                if (currentToken.jumpInsn.opcode != Opcodes.GOTO) {
                    token2Tree.matchToken2Tree(nextIndex, currentNode)
                }
            }

            is SwitchToken -> {
                currentNode.tokens += currentToken

                val branches = (currentToken.branches + currentToken.default).map {
                    labels[it.label] ?: return Failure("Switch target label ${it.label} not found").toRight()
                }
                for (branchIndex in branches) {
                    /* All 'switch' branches are expected to be forward */
                    /* This is just a safety guard to prevent unexpected situations */
                    if (branchIndex > currentIndex) {
                        token2Tree.matchToken2Tree(branchIndex, currentNode)
                    } else {
                        logger.warn("Unexpected 'switch' backward jump at index $currentIndex")
                    }
                }
            }

            is ReturnToken -> {
                if (currentNode.type != ScopeType.Method) {
                    return Failure("ReturnToken is not allowed in ${currentNode.type} scope").toRight()
                }
                currentNode.tokens += currentToken
            }

            is CurrentMarkerToken -> {
                currentNode.tokens += currentToken
                currentNode.markers += currentToken.variableIndex
                token2Tree.matchToken2Tree(nextIndex, currentNode)
            }

            is EndToMarkerToken -> {
                currentNode.tokens += currentToken
                when (currentToken.variableIndex) {
                    in currentNode.markers -> {
                        token2Tree.matchToken2Tree(nextIndex, currentNode)
                    }
                    else -> {
                        var parent = currentNode
                        do {
                            parent = parent.parent
                                ?: return Failure(
                                    "EndToMarkerToken(${currentToken.variableIndex}) " +
                                        "is not matched in ${currentNode.type} scope"
                                ).toRight()
                        } while (currentToken.variableIndex !in parent.markers)
                        token2Tree.matchToken2Tree(nextIndex, parent)
                    }
                }
            }
        }
    }

    return root.toInstructionTree().toLeft()
}
