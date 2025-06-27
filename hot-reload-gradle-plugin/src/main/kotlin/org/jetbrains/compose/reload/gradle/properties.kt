/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.gradle

import org.gradle.api.Project
import org.gradle.api.provider.Provider

/**
 * true, if the current build was started by 'Compose Hot Reload' with the intention of quickly 'recompiling' the code
 * false otherwise.
 *
 * See [org.jetbrains.compose.reload.core.HotReloadProperty.IsHotReloadBuild]
 */
val Project.isHotReloadBuild: Boolean get() = composeReloadIsHotReloadBuild

internal val Project.isIdeaSync: Provider<Boolean>
    get() = providers.systemProperty("idea.sync.active").map { raw -> raw.toBoolean() }
        .orElse(false)

internal val Project.isIdea: Provider<Boolean>
    get() = providers.systemProperty("idea.active").map { raw -> raw.toBooleanStrictOrNull() ?: true }
        .orElse(false)
