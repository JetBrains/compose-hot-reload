/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.test.gradle

import org.jetbrains.kotlin.tooling.core.extrasKeyOf

public enum class ProjectMode : HotReloadTestDimension {
    Kmp, Jvm;

    override fun displayName(): String {
        return name
    }

    override fun toString(): String {
        return name
    }

    public companion object {
        internal val key = extrasKeyOf<ProjectMode>()
    }
}

public fun <T> ProjectMode.fold(kmp: T, jvm: T): T =
    when (this) {
        ProjectMode.Kmp -> kmp
        ProjectMode.Jvm -> jvm
    }
