/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.devtools.gradle

import org.jetbrains.compose.devtools.api.Recompiler
import org.jetbrains.compose.devtools.api.RecompilerExtension
import org.jetbrains.compose.devtools.sendAsync
import org.jetbrains.compose.reload.core.BuildSystem.Gradle
import org.jetbrains.compose.reload.core.HotReloadEnvironment
import org.jetbrains.compose.reload.core.HotReloadEnvironment.gradleBuildRoot
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.error
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import java.nio.file.Path

internal class GradleRecompilerExtension : RecompilerExtension {
    private val logger = createLogger()

    override fun createRecompiler(): Recompiler? {
        if (HotReloadEnvironment.buildSystem != Gradle) return null
        val gradleBuildRoot: Path = gradleBuildRoot ?: run {
            logger.error("Missing '${HotReloadEnvironment::gradleBuildRoot.name}' property")
            return null
        }

        val gradleBuildProject: String = HotReloadEnvironment.gradleBuildProject ?: run {
            logger.error("Missing '${HotReloadEnvironment::gradleBuildProject.name}' property")
            return null
        }

        val gradleBuildTask: String = HotReloadEnvironment.gradleBuildTask ?: run {
            logger.error("Missing '${HotReloadEnvironment::gradleBuildTask.name}' property")
            return null
        }

        /* Side Effect */
        if (HotReloadEnvironment.gradleBuildContinuous) {
            OrchestrationMessage.RecompileRequest().sendAsync()
        }

        return GradleRecompiler(
            buildRoot = gradleBuildRoot,
            buildProject = gradleBuildProject,
            buildTask = gradleBuildTask,
        )
    }
}
