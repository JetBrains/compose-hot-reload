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
    val startIndex: Int,
    val lastIndex: Int,
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
            startIndex = -1, lastIndex = -1, tokens = emptyList(), children = emptyList()
        )
    }

    val tokens = tokenizeRuntimeInstructions(methodNode.instructions.toList()).leftOr { right ->
        /* Fallback for methods that even fail to tokenize */
        logger.warn("'tokenizeRuntimeInstructions' failed on $methodId: $right")
        val tokens = listOf(BlockToken(methodNode.instructions.toList()))
        return RuntimeInstructionTree(
            group = methodNode.readFunctionKeyMetaAnnotation(),
            type = RuntimeScopeType.Method,
            startIndex = 0,
            lastIndex = tokens.lastIndex,
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
            startIndex = 0,
            lastIndex = tokens.lastIndex,
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
    val groupKey = methodNode.readFunctionKeyMetaAnnotation()
    return parseRuntimeInstructionTree(
        groupKey, RuntimeScopeType.Method, tokens, startIndex = 0, endIndex = tokens.lastIndex
    )
}

private fun parseRuntimeInstructionTree(
    group: ComposeGroupKey?,
    type: RuntimeScopeType,
    tokens: List<RuntimeInstructionToken>,
    /**
     * The index to start parsing from
     */
    startIndex: Int,

    /**
     * The index (exclusive) where to stop parsing!
     */
    endIndex: Int,

    consumed: List<RuntimeInstructionToken> = mutableListOf(),
    children: List<RuntimeInstructionTree> = mutableListOf(),
): Either<RuntimeInstructionTree, Failure> {
    if (tokens.isEmpty()) return Failure("empty tokens").toRight()
    val consumed = consumed.toMutableList()
    val children = children.toMutableList()
    val marker = mutableListOf<CurrentMarkerToken>()

    var index = startIndex
    while (index < tokens.size) {
        if (index > endIndex) return Failure("Scope ended").toRight()

        val currentToken = tokens[index]
        val currentIndex = index

        when (currentToken) {
            is BlockToken, is LabelToken,
            is SourceInformation, is SourceInformationMarkerStart, is SourceInformationMarkerEnd -> {
                consumed += currentToken
                index++
            }

            is CurrentMarkerToken -> {
                marker += currentToken
                consumed += currentToken
                index++
            }

            is EndToMarkerToken -> {
                if (marker.any { it.variableIndex == currentToken.variableIndex }) {
                    index++
                } else {
                    /* Yield the token to the parent, by only consuming it when its part of this scope */
                    consumed += currentToken
                    index--
                    break
                }
            }

            is ReturnToken -> {
                if (type != RuntimeScopeType.Method) {
                    return Failure("ReturnToken is not allowed in $type scope").toRight()
                }
                consumed += currentToken
                break
            }

            is EndRestartGroup -> {
                if (type != RuntimeScopeType.RestartGroup) {
                    return Failure("EndRestartGroup is not allowed in $type scope").toRight()
                }
                consumed += currentToken
                break
            }

            is EndReplaceGroup -> {
                if (type != RuntimeScopeType.ReplaceGroup) {
                    return Failure("EndReplaceGroup is not allowed in $type scope").toRight()
                }
                consumed += currentToken
                break
            }

            is StartRestartGroup -> {
                val child = parseRuntimeInstructionTree(
                    group = currentToken.key,
                    type = RuntimeScopeType.RestartGroup,
                    tokens = tokens,
                    startIndex = currentIndex + 1,
                    endIndex = endIndex,
                    consumed = listOf(currentToken)
                ).leftOr { return it }

                children += child
                index = index + child.lastIndex + 1
            }


            is StartReplaceGroup -> {
                val child = parseRuntimeInstructionTree(
                    group = currentToken.key,
                    type = RuntimeScopeType.ReplaceGroup,
                    tokens = tokens,
                    startIndex = currentIndex + 1,
                    endIndex = endIndex,
                    consumed = listOf(currentToken)
                ).leftOr { return it }
                children += child
                index = child.lastIndex + 1
            }

            is JumpToken -> {
                consumed += currentToken

                /* Perform Jump */
                val jumpIndex = tokens.indexOfFirst {
                    it is LabelToken && it.labelInsn.label == currentToken.jumpInsn.label
                }

                /* Loop jump; We only care about forward jumps atm */
                if (jumpIndex <= index) {
                    index++
                    continue
                }

                /* Perform the forward jump */
                children += parseRuntimeInstructionTree(
                    group = group,
                    type = type,
                    tokens = tokens,
                    startIndex = tokens.indexOfFirst {
                        it is LabelToken && it.labelInsn.label == currentToken.jumpInsn.label
                    },
                    endIndex = endIndex
                ).leftOr { return it }


                /* Continue execution on path w/o jump */
                if (currentToken.jumpInsn.opcode != Opcodes.GOTO) {
                    val child = parseRuntimeInstructionTree(
                        group = group,
                        type = type,
                        tokens = tokens,
                        startIndex = currentIndex + 1,
                        endIndex = jumpIndex,
                    ).leftOr { return it }
                    children += child
                    index = child.lastIndex + 1
                }

                break
            }
        }
    }

    return RuntimeInstructionTree(
        group = group,
        type = type,
        tokens = consumed,
        children = children,
        startIndex = startIndex,
        lastIndex = index
    ).toLeft()
}
