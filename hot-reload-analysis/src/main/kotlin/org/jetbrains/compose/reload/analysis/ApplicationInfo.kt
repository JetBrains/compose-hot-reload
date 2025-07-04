/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

@file:Suppress("DuplicatedCode")

package org.jetbrains.compose.reload.analysis

sealed interface ApplicationInfo {
    /**
     * Index from a known classId to its ClassInfo
     */
    val classIndex: Map<ClassId, ClassInfo>

    /**
     * Index from a known methodId to its MethodInfo
     */
    val methodIndex: Map<MethodId, MethodInfo>

    /**
     * Index from a known fieldId to the FieldInfo
     */
    val fieldIndex: Map<FieldId, FieldInfo>

    /**
     * Index from the known compose group key to the list of associated runtime scopes
     */
    val groupIndex: Map<ComposeGroupKey?, Collection<ScopeInfo>>

    /**
     * Index from a class to all its superclasses
     */
    val superIndex: Map<ClassId, Collection<ClassId>>

    /**
     * Index from a classId to all its implementations
     */
    val superIndexInverse: Map<ClassId, Collection<ClassId>>

    /**
     * Index from any member (field, method), to all depending on scopes
     */
    val dependencyIndex: Map<MemberId, Collection<ScopeInfo>>
}
