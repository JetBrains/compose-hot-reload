/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.analysis

import org.jetbrains.compose.reload.InternalHotReloadApi
import org.jetbrains.compose.reload.analysis.ScopeInfo.SourceLocation
import org.jetbrains.compose.reload.core.Context
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode

@ConsistentCopyVisibility
data class ScopeInfo @InternalHotReloadApi internal constructor(
    val methodId: MethodId,
    val scopeType: ScopeType,
    val scopeHash: ScopeHash,
    val group: ComposeGroupKey?,
    val methodDependenciesList: List<MethodId>,
    val fieldDependenciesList: List<FieldId>,
    val children: List<ScopeInfo>,
    val extras: Context,
    val sourceLocation: SourceLocation,
) {
    @Deprecated("Use methodDependenciesList instead", ReplaceWith("methodDependenciesList"))
    val methodDependencies: Set<MethodId> get() = methodDependenciesList.toSet()

    @Deprecated("Use fieldDependenciesList instead", ReplaceWith("fieldDependenciesList"))
    val fieldDependencies: Set<FieldId> get() = fieldDependenciesList.toSet()

    @ConsistentCopyVisibility
    data class SourceLocation @InternalHotReloadApi internal constructor(
        val sourceFile: String?,
        val firstLineNumber: Int?,
    )
}

enum class ScopeType {
    Method, RestartGroup, ReplaceGroup, SourceInformationMarker,
}

internal fun ScopeInfo(
    classNode: ClassNode,
    methodNode: MethodNode
): ScopeInfo {
    val methodId = MethodId(classNode, methodNode)
    val runtimeInstructionTree = parseInstructionTreeLenient(methodId, methodNode)
    val sourceFile = classNode.sourceFile
    return createScopeInfo(methodId, methodNode, runtimeInstructionTree, sourceFile)
}

internal fun createScopeInfo(
    methodId: MethodId,
    methodNode: MethodNode,
    tree: InstructionTree,
    sourceFile: String?,
): ScopeInfo {
    return ScopeInfo(
        methodId = methodId,
        scopeType = tree.type,
        scopeHash = tree.scopeHash(methodNode),
        group = tree.group,
        methodDependenciesList = tree.methodDependencies(),
        fieldDependenciesList = tree.fieldDependencies(),
        children = tree.children.map { child -> createScopeInfo(methodId, methodNode, child, sourceFile) }.toList(),
        extras = createScopeInfoExtras(methodId, methodNode, tree),
        sourceLocation = SourceLocation(
            sourceFile = sourceFile?.interned(),
            firstLineNumber = tree.tokens.firstLineNumber,
        )
    )
}


private val List<InstructionToken>.firstLineNumber: Int?
    get() = minOfOrNull min@{
        val label = it as? InstructionToken.LabelToken ?: return@min Int.MAX_VALUE
        label.lineNumberInsn?.line ?: Int.MAX_VALUE
    }.takeIf { it != Int.MAX_VALUE }