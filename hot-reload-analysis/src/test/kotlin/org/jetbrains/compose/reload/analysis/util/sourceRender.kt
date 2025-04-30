/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.analysis.util

import org.jetbrains.compose.reload.analysis.ClassNode
import org.jetbrains.compose.reload.analysis.parseRuntimeInstructionTree
import org.jetbrains.compose.reload.analysis.tokenizeRuntimeInstructions
import org.jetbrains.compose.reload.core.leftOr
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.LineNumberNode


internal fun renderSourceTree(source: String, bytecode: ByteArray): String {
    return ClassNode(bytecode).renderSourceTree(source)
}

internal fun ClassNode.renderSourceTree(
    source: String
): String {
    val sourceLines = source.lines()
    val line2Tree = MutableList(sourceLines.size) { linkedSetOf<String>() }

    methods.sortedBy { node -> node.name + node.desc }.forEachIndexed { index, methodNode ->
        val methodTokens = tokenizeRuntimeInstructions(methodNode.instructions.toList())
            .leftOr { error("Failed to tokenize a method: $it") }
        val tree = parseRuntimeInstructionTree(methodNode, methodTokens)
            .leftOr { error("Failed to parse runtime instruction tree: $it") }

        methodNode.verifyTree(methodTokens, tree)
        val inst2Tree = tree.inst2TreeMapping()

        var currentLine = -1
        for (inst in methodNode.instructions) {
            if (inst is LineNumberNode) {
                currentLine = inst.line - 1
            }
            val lineTrees = line2Tree.getOrNull(currentLine)
            val instTree = inst2Tree[inst]

            if (lineTrees != null && instTree != null) {
                lineTrees += instTree.render()
            }
        }
    }

    return source.lines().mapIndexed { index, line ->
        val lineTrees = line2Tree[index].joinToString(",", prefix = "\t// ")
        when {
            line2Tree[index].isEmpty() -> line
            else -> "${line.padEnd(60)}$lineTrees"
        }
    }.joinToString("\n")
}
