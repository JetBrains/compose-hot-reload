/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.analysis

import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode

data class ScopeInfo(
    val methodId: MethodId,
    val methodType: MethodType,
    val scopeType: ScopeType,
    val scopeHash: ScopeHash,
    val group: ComposeGroupKey?,
    val methodDependencies: Set<MethodId>,
    val fieldDependencies: Set<FieldId>,
    val children: List<ScopeInfo>,
)

enum class ScopeType {
    Method, RestartGroup, ReplaceGroup, SourceInformationMarker,
}

internal fun ScopeInfo(
    classNode: ClassNode,
    methodNode: MethodNode
): ScopeInfo {
    val methodId = MethodId(classNode, methodNode)
    val runtimeInstructionTree = parseInstructionTreeLenient(methodId, methodNode)
    return createScopeInfo(methodId, methodNode, runtimeInstructionTree)
}

internal fun createScopeInfo(
    methodId: MethodId,
    methodNode: MethodNode,
    tree: InstructionTree,
): ScopeInfo {
    return ScopeInfo(
        methodId = methodId,
        methodType = MethodType(methodNode),
        scopeType = tree.type,
        scopeHash = tree.scopeHash(methodNode),
        group = tree.group,
        methodDependencies = tree.methodDependencies(),
        fieldDependencies = tree.fieldDependencies(),
        children = tree.children.map { child -> createScopeInfo(methodId, methodNode, child) },
    )
}
