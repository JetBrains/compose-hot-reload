/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.analysis

import org.jetbrains.compose.reload.core.withClosure

sealed class MemberInfo {
    abstract val memberId: MemberId
}

@ConsistentCopyVisibility
data class FieldInfo internal constructor(
    val fieldId: FieldId,
    val isStatic: Boolean,
    val initialValue: Any?,
    /** An optional hash used as an additional indicator to track changes to the field */
    val additionalChangeIndicatorHash: Int?,
) : MemberInfo() {
    override val memberId: FieldId get() = fieldId
}

@ConsistentCopyVisibility
data class MethodInfo internal constructor(
    val methodId: MethodId,
    val methodType: MethodType,
    val modality: Modality,
    val rootScope: ScopeInfo,
) : MemberInfo() {
    override val memberId: MethodId get() = methodId

    enum class Modality {
        FINAL, OPEN, ABSTRACT
    }
}

val MethodInfo.allScopes: Set<ScopeInfo>
    get() = rootScope.withClosure { scope -> scope.children }
