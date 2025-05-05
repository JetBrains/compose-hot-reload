/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.compose.reload.gradle.PluginStage
import org.jetbrains.compose.reload.gradle.launch
import org.jetbrains.compose.reload.gradle.runStage
import org.jetbrains.compose.reload.gradle.withKotlinPlugin

@Suppress("unused")
class ComposeHotReloadPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.runStage(PluginStage.PluginApplied) {
            if (!target.hasComposePluginAccess()) return
            if (!target.hasKotlinPluginAccess()) return
            target.extensions.create(composeHotReloadExtensionName, ComposeHotReloadExtension::class.java, target)

            /* Launch future configurations */
            target.launch { target.hotRunTasks.await() }
            target.launch { target.jvmRunHijackTasks.await() }
            target.launch { target.hotAsyncRunTasks.await() }
            target.launch { target.hotArgFileTasks.await() }
            target.launch { target.hotProcessManagerTask.await() }
            target.launch { target.hotDevCompilations.await() }
            target.launch { target.hotReloadLifecycleTask.await() }
            target.launch { target.hotReloadTasks.await() }
            target.launch { target.hotSnapshotTasks.await() }
            target.configureComposeCompilerArgs()
            target.configureComposeHotReloadModelBuilder()
            target.configureComposeHotReloadAttributes()
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
