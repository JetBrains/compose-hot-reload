/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.analysis

import org.jetbrains.compose.reload.InternalHotReloadApi
import org.jetbrains.compose.reload.analysis.ScopeType.Method
import org.jetbrains.compose.reload.analysis.ScopeType.ReplaceGroup
import org.jetbrains.compose.reload.analysis.ScopeType.RestartGroup
import org.jetbrains.compose.reload.analysis.ScopeType.SourceInformationMarker
import org.jetbrains.compose.reload.core.asMap
import org.jetbrains.compose.reload.core.leftOr
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.JumpInsnNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode

@InternalHotReloadApi
fun ApplicationInfo.render(): String = buildString {
    classIndex.toSortedMap().forEach { (_, classInfo) ->
        append(classInfo.render())
        appendLine()
    }
}

@InternalHotReloadApi
fun ClassInfo.render(): String = buildString {
    appendLine("$classId {")

    if (fields.isNotEmpty()) {
        appendLine(fields.values.joinToString("\n") { it.render() }.indent())
        appendLine()
    }

    withIndent {
        appendLine(methods.values.joinToString("\n\n") { it.render() })
    }


    appendLine("}")
}

internal fun MethodInfo.render(): String = buildString {
    appendLine("${methodId.methodName} {")
    withIndent {
        appendLine("desc: ${methodId.methodDescriptor}")
        appendLine("type: $methodType")
        appendLine(rootScope.render())
    }
    this += "}"
}

internal fun ScopeInfo.render(): String = buildString {
    when (scopeType) {
        Method -> appendLine("Method {")
        RestartGroup -> appendLine("RestartGroup {")
        ReplaceGroup -> appendLine("ReplaceGroup {")
        SourceInformationMarker -> appendLine("SourceInformationMarker {")
    }

    withIndent {
        appendLine("key: ${group?.key}")
        appendLine("codeHash: ${scopeHash.value}")
        if (methodDependencies.isEmpty()) {
            appendLine("methodDependencies: []")
        } else {
            appendLine("methodDependencies: [")
            withIndent {
                append(methodDependencies.joinToString(",\n"))
            }
            appendLine("]")
        }

        if (fieldDependencies.isEmpty()) {
            appendLine("fieldDependencies: []")
        } else {
            appendLine("fieldDependencies: [")
            withIndent {
                append(fieldDependencies.joinToString(",\n"))
            }
            appendLine("]")
        }

        if (children.isNotEmpty()) {
            appendLine()
            appendLine(children.joinToString("\n\n") { it.render() }).trim()
            appendLine()
        }

        if (extras.asMap().isNotEmpty()) {
            extras.asMap().forEach { (key, value) ->
                appendLine("$key: $value")
            }
        }
    }

    this += "}"
}.trim()

@InternalHotReloadApi
fun FieldInfo.render(): String = "val ${fieldId.fieldName}: ${fieldId.fieldDescriptor}"

@InternalHotReloadApi
fun MethodNode.render(tokens: List<InstructionToken>): String = buildString {
    appendLine("fun ${name}${desc} {")
    withIndent {
        tokens.forEach { token ->
            appendLine(render(token))
            withIndent {
                token.instructions.forEach { instruction ->
                    appendLine(render(instruction))
                }
            }
        }
    }
    appendLine("}")
}

private fun MethodNode.indexOfLabel(label: Label): Int {
    return instructions.filterIsInstance<LabelNode>().indexOfFirst { node -> node.label == label }
}

private fun MethodNode.render(token: InstructionToken): String {
    return when (token) {
        is InstructionToken.LabelToken -> "LabelToken(L${indexOfLabel(token.labelInsn.label)})"
        is InstructionToken.JumpToken -> "JumpToken(L${indexOfLabel(token.jumpInsn.label.label)}, opocde=${token.jumpInsn.opcode})"
        else -> token.toString()
    }
}

private fun MethodNode.render(node: AbstractInsnNode): String {
    return when (node) {
        is MethodInsnNode -> "MethodInsnNode(${MethodId(node)}) [${node.opcode}]"
        is LdcInsnNode -> "LdcInsnNode(${node.cst}) [${node.opcode}]"
        is LabelNode -> "LabelNode(L${indexOfLabel(node.label)})"
        is JumpInsnNode -> "JumpInsNode(L${indexOfLabel(node.label.label)})) " +
            "[${if (node.opcode == Opcodes.GOTO) "GOTO" else node.opcode}])"

        else -> "${node.javaClass.simpleName} [${node.opcode}]"
    }
}

@InternalHotReloadApi
fun renderInstructionTree(bytecode: ByteArray): String {
    return ClassNode(bytecode).renderInstructionTree()
}

@InternalHotReloadApi
fun ClassNode.renderInstructionTree(): String = buildString {
    appendLine("class ${name.replace("/", ".")} {")
    withIndent {
        methods.sortedBy { node -> node.name + node.desc }.forEachIndexed { index, methodNode ->
            this += "fun ${methodNode.name} ${methodNode.desc} {"

            val tree = parseInstructionTree(methodNode)
                .leftOr { right -> error("Failed to parse runtime instruction tree: $right") }

            this += methodNode.render(tree).indent()
            this += "}"
            if (index != methods.lastIndex) {
                appendLine()
            }
        }
    }
    appendLine("}")
}

@InternalHotReloadApi
fun MethodNode.render(tree: InstructionTree): String = buildString {
    this += "${tree.type} (group=${tree.group}) {"
    this += tree.tokens.joinToString("\n") { token -> render(token).trim() }.indent()

    if (tree.children.isNotEmpty()) {
        appendLine()
        this += tree.children.joinToString("\n\n") { tree -> render(tree) }.indent()
    }

    this += "}"
}.trim()


internal operator fun StringBuilder.plusAssign(str: String) {
    appendLine(str)
}

internal fun StringBuilder.withIndent(builder: StringBuilder.() -> Unit) {
    appendLine(buildString(builder).trim().prependIndent("    "))
}

internal fun String.indent(n: Int = 1) = prependIndent("    ".repeat(n))
