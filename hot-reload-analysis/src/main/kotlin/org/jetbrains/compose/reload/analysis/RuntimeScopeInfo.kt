/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.analysis

data class RuntimeScopeInfo(
    val methodId: MethodId,
    val methodType: MethodType,
    val scopeType: RuntimeScopeType,
    val group: ComposeGroupKey?,
    val methodDependencies: Set<MethodId>,
    val fieldDependencies: Set<FieldId>,
    val children: List<RuntimeScopeInfo>,
    val hash: RuntimeInstructionTreeCodeHash,
)
