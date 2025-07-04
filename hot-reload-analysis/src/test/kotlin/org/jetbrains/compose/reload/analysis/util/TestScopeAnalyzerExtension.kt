/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.analysis.util

import org.jetbrains.compose.reload.analysis.InstructionTree
import org.jetbrains.compose.reload.analysis.MethodId
import org.jetbrains.compose.reload.analysis.ScopeAnalyzerExtension
import org.jetbrains.compose.reload.core.Context
import org.objectweb.asm.tree.MethodNode

class TestScopeAnalyzerExtension : ScopeAnalyzerExtension {
    override fun analyze(methodId: MethodId, methodNode: MethodNode, tree: InstructionTree): Context {
        return local.get()?.analyze(methodId, methodNode, tree) ?: Context()
    }

    companion object {
        val local = ThreadLocal<ScopeAnalyzerExtension>()
        fun <T> with(extension: ScopeAnalyzerExtension, block: () -> T): T {
            local.set(extension)
            try {
                return block()
            } finally {
                local.set(null)
            }
        }
    }
}
