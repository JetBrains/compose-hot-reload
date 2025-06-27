/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.gradle

import org.gradle.api.Project
import org.jetbrains.compose.reload.gradle.idea.IdeaComposeHotReloadSupportVersions

/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

internal val Project.ideIsRecompileContinuousMode get() =
    project.composeReloadIdeaComposeHotReloadProvider.map { idePlugin ->
        if (!idePlugin) return@map null
        val supportVersion = project.composeReloadIdeaComposeHotReloadSupportVersion ?: return@map null
        supportVersion < IdeaComposeHotReloadSupportVersions.supportReloadTasks
    }
