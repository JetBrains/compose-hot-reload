/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.analysis

import org.jetbrains.compose.reload.core.Context
import org.jetbrains.compose.reload.core.EmptyContext
import org.jetbrains.compose.reload.core.plus
import org.objectweb.asm.tree.MethodNode
import java.util.ServiceLoader

/**
 * Can be used by external components to extend the analysis with custom information.
 * Each [InstructionTree] will call this [analyze] method when converting to [ScopeInfo].
 *
 * The returned [Context] will be accumulated across all extensions and can contain anonymous
 * values, which can later be accessed in [ScopeInfo.extras]
 */
interface ScopeAnalyzerExtension {
    fun analyze(methodId: MethodId, methodNode: MethodNode, tree: InstructionTree): Context
}

private val extensions = ServiceLoader.load(
    ScopeAnalyzerExtension::class.java, ClassLoader.getSystemClassLoader()
).toList()


internal fun createScopeInfoExtras(
    methodId: MethodId, methodNode: MethodNode, tree: InstructionTree
): Context {
    if (extensions.isEmpty()) return EmptyContext
    return extensions.fold(Context()) { acc, extension ->
        acc + extension.analyze(methodId, methodNode, tree)
    }
}
