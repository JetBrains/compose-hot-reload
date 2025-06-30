/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.jetbrains.compose.reload.gradle.ComposeHotTask
import org.jetbrains.compose.reload.gradle.PluginStage
import org.jetbrains.compose.reload.gradle.await
import org.jetbrains.compose.reload.gradle.camelCase
import org.jetbrains.compose.reload.gradle.forAllJvmTargets
import org.jetbrains.compose.reload.gradle.hotRunTask
import org.jetbrains.compose.reload.gradle.launch

internal fun Project.createCompatibilityTasks() = launch {
    PluginStage.EagerConfiguration.await()

    forAllJvmTargets { target ->
        @Suppress("DEPRECATION")
        val deprecatedTask = project.tasks.register(camelCase(target.name, "run", "hot"), ComposeHotRun::class.java)
        val newTask = target.hotRunTask.await()
        val newTaskName = newTask?.name
        deprecatedTask.configure { task ->
            task.group = ComposeHotTask.COMPOSE_HOT_RELOAD_RUN_GROUP
            task.description = "Deprecated: Use '${newTask?.name}' instead"
            task.finalizedBy(newTask)
            task.doFirst {
                error("Deprecated task '${task.name}' is used. Please use '${newTaskName}' instead.")
            }
        }
    }
}

@Deprecated(
    "Use org.jetbrains.compose.reload.gradle.ComposeHotRun instead",
    ReplaceWith("ComposeHotRun", "org.jetbrains.compose.reload.gradle.ComposeHotRun")
)
open class ComposeHotRun : DefaultTask()
