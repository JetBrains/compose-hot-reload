/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.analysis

import org.jetbrains.compose.reload.analysis.RuntimeInstructionToken.BlockToken
import org.jetbrains.compose.reload.analysis.RuntimeInstructionToken.CurrentMarkerToken
import org.jetbrains.compose.reload.analysis.RuntimeInstructionToken.EndReplaceGroup
import org.jetbrains.compose.reload.analysis.RuntimeInstructionToken.EndRestartGroup
import org.jetbrains.compose.reload.analysis.RuntimeInstructionToken.EndToMarkerToken
import org.jetbrains.compose.reload.analysis.RuntimeInstructionToken.JumpToken
import org.jetbrains.compose.reload.analysis.RuntimeInstructionToken.LabelToken
import org.jetbrains.compose.reload.analysis.RuntimeInstructionToken.ReturnToken
import org.jetbrains.compose.reload.analysis.RuntimeInstructionToken.SourceInformation
import org.jetbrains.compose.reload.analysis.RuntimeInstructionToken.SourceInformationMarkerEnd
import org.jetbrains.compose.reload.analysis.RuntimeInstructionToken.SourceInformationMarkerStart
import org.jetbrains.compose.reload.analysis.RuntimeInstructionToken.StartReplaceGroup
import org.jetbrains.compose.reload.analysis.RuntimeInstructionToken.StartRestartGroup
import org.jetbrains.compose.reload.core.Either
import org.jetbrains.compose.reload.core.Failure
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.leftOr
import org.jetbrains.compose.reload.core.toLeft
import org.jetbrains.compose.reload.core.toRight
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.MethodNode

private val logger = createLogger()

data class RuntimeInstructionTree(
    val group: ComposeGroupKey?,
    val type: RuntimeScopeType,
    val tokens: List<RuntimeInstructionToken>,
    val children: List<RuntimeInstructionTree>,

    /**
     * Indicating that this tree was built with some failure. Using this tree requires caution!
     */
    val failure: Failure? = null,
)

internal fun parseRuntimeInstructionTreeLenient(methodId: MethodId, methodNode: MethodNode): RuntimeInstructionTree {
    /* Handle methods w/o bodies */
    if (methodNode.instructions.size() == 0) {
        return RuntimeInstructionTree(
            group = methodNode.readFunctionKeyMetaAnnotation(), type = RuntimeScopeType.Method,
            tokens = emptyList(), children = emptyList()
        )
    }

    val tokens = tokenizeRuntimeInstructions(methodNode.instructions.toList()).leftOr { right ->
        /* Fallback for methods that even fail to tokenize */
        logger.warn("'tokenizeRuntimeInstructions' failed on $methodId: $right")
        val tokens = listOf(BlockToken(methodNode.instructions.toList()))
        return RuntimeInstructionTree(
            group = methodNode.readFunctionKeyMetaAnnotation(),
            type = RuntimeScopeType.Method,
            tokens = tokens,
            children = emptyList(),
            failure = right.value
        )
    }

    return parseRuntimeInstructionTree(methodNode, tokens).leftOr { right ->
        logger.warn("'parseRuntimeInstructionTree' failed on $methodId: $right")
        return RuntimeInstructionTree(
            group = methodNode.readFunctionKeyMetaAnnotation(),
            type = RuntimeScopeType.Method,
            tokens = tokens,
            children = emptyList(),
            failure = right.value
        )
    }
}

internal fun parseRuntimeInstructionTree(methodNode: MethodNode): Either<RuntimeInstructionTree, Failure> {
    return parseRuntimeInstructionTree(
        methodNode, tokenizeRuntimeInstructions(methodNode.instructions.toList()).leftOr { return it }
    )
}

internal fun parseRuntimeInstructionTree(
    methodNode: MethodNode, tokens: List<RuntimeInstructionToken>
): Either<RuntimeInstructionTree, Failure> {
    return linearParseRuntimeInstructionTree(methodNode, tokens)
}

private class MutableTree(
    var group: ComposeGroupKey?,
    var type: RuntimeScopeType,
    val tokens: MutableList<RuntimeInstructionToken>,
    val children: MutableList<MutableTree>,
    var parent: MutableTree? = null,
    val markers: MutableSet<Int> = mutableSetOf()
) {
    fun toRuntimeInstructionTree(): RuntimeInstructionTree = RuntimeInstructionTree(
        group = this.group,
        type = this.type,
        tokens = this.tokens,
        children = this.children.map { it.toRuntimeInstructionTree() },
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

private fun linearParseRuntimeInstructionTree(
    methodNode: MethodNode, tokens: List<RuntimeInstructionToken>
): Either<RuntimeInstructionTree, Failure> {
    if (tokens.isEmpty()) return Failure("empty tokens").toRight()

    val token2Tree = MutableList<MutableTree?>(tokens.size + 1) { null }
    val root = MutableTree(
        group = methodNode.readFunctionKeyMetaAnnotation(),
        type = RuntimeScopeType.Method,
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
                    type = RuntimeScopeType.RestartGroup,
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
                if (currentNode.type != RuntimeScopeType.RestartGroup) {
                    return Failure("EndRestartGroup is not allowed in ${currentNode.type} scope").toRight()
                }
                currentNode.tokens += currentToken
                token2Tree.matchToken2Tree(nextIndex, currentNode.parent)
            }

            is StartReplaceGroup -> {
                val newNode = MutableTree(
                    group = currentToken.key,
                    type = RuntimeScopeType.ReplaceGroup,
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
                if (currentNode.type != RuntimeScopeType.ReplaceGroup) {
                    return Failure("EndReplaceGroup is not allowed in ${currentNode.type} scope").toRight()
                }
                currentNode.tokens += currentToken
                token2Tree.matchToken2Tree(nextIndex, currentNode.parent)
            }

            is JumpToken -> {
                currentNode.tokens += currentToken

                val jumpIndex = tokens.indexOfFirst {
                    it is LabelToken && it.labelInsn.label == currentToken.jumpInsn.label.label
                }

                /* Perform the forward jump; We don't care about backward jumps, because they are already handled */
                if (jumpIndex > currentIndex) {
                    token2Tree.matchToken2Tree(jumpIndex, currentNode)
                }

                /* Continue execution on path w/o jump */
                if (currentToken.jumpInsn.opcode != Opcodes.GOTO) {
                    token2Tree.matchToken2Tree(nextIndex, currentNode)
                }
            }

            is ReturnToken -> {
                if (currentNode.type != RuntimeScopeType.Method) {
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
                                ?: return Failure("EndToMarkerToken(${currentToken.variableIndex}) is not matched in ${currentNode.type} scope").toRight()
                        } while (currentToken.variableIndex !in parent.markers)
                        token2Tree.matchToken2Tree(nextIndex, parent)
                    }
                }
            }
        }
    }

    return root.toRuntimeInstructionTree().toLeft()
}
