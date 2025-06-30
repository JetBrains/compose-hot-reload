/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.gradle

import org.gradle.api.Project
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.internal.build.BuildState
import org.jetbrains.compose.reload.InternalHotReloadApi
import java.io.Serializable


@InternalHotReloadApi
val Project.currentBuild: CurrentBuild
    get() = CurrentBuild(
        runCatching { (this as ProjectInternal).services.get(BuildState::class.java).buildIdentifier.buildPath }
            .getOrElse { exception ->
                logger.error("Failed to get current 'buildIdentifier'", exception)
                ":"
            }
    )

@ConsistentCopyVisibility
@InternalHotReloadApi
data class CurrentBuild internal constructor(private val currentBuildPath: String) : Serializable {
    operator fun contains(identifier: ComponentIdentifier): Boolean {
        return identifier is ProjectComponentIdentifier && identifier.build.buildPath == currentBuildPath
    }

    @InternalHotReloadApi
    companion object {
        val default = CurrentBuild(":")
    }
}
