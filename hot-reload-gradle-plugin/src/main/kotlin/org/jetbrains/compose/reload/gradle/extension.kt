/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.gradle

import org.gradle.api.Project

internal const val composeHotReloadExtensionName = "composeHotReload"

internal val Project.composeHotReloadExtension: ComposeHotReloadExtension
    get() = extensions.getByType(ComposeHotReloadExtension::class.java)

open class ComposeHotReloadExtension(internal val project: Project)
