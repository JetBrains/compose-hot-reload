/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

@file:Suppress("DuplicatedCode")

package org.jetbrains.compose.reload.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.compose.reload.createCompatibilityTasks

@Suppress("unused")
class ComposeHotReloadPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.runStage(PluginStage.PluginApplied) {
            /* Configuration which is expected to always happen on plugin apply */
            target.configureComposeHotReloadAttributes()
            target.extensions.create(composeHotReloadExtensionName, ComposeHotReloadExtension::class.java, target)

            /* Defend further configuration against situations where collaborative plugin access is not available */
            if (!target.hasComposePluginAccess()) return
            if (!target.hasKotlinPluginAccess()) return

            /* Launch future configurations */
            target.launch { target.createCompatibilityTasks() }
            target.launch { target.statusService.await() }
            target.launch { target.hotRunTasks.await() }
            target.launch { target.hotAsyncRunTasks.await() }
            target.launch { target.hotArgFileTasks.await() }
            target.launch { target.hotDevCompilations.await() }
            target.launch { target.hotReloadLifecycleTask.await() }
            target.launch { target.hotReloadTasks.await() }
            target.launch { target.hotSnapshotTasks.await() }
            target.configureComposeHotReloadTasks()
            target.configureComposeCompilerArgs()
            target.configureComposeHotReloadModelBuilder()
            target.configureResourceAccessorsGenerationTasks()
        }

        target.withKotlinPlugin(target::onKotlinPluginApplied)
    }
}

private fun Project.onKotlinPluginApplied() {
    runStage(PluginStage.EagerConfiguration)

    afterEvaluate {
        afterEvaluate {
            runStage(PluginStage.DeferredConfiguration)
        }
    }
}
