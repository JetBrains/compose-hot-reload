/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.compose.reload.gradle.withKotlinPlugin

@Suppress("unused")
class ComposeHotReloadPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        if (!target.hasComposePluginAccess()) return
        if (!target.hasKotlinPluginAccess()) return

        target.extensions.create(composeHotReloadExtensionName, ComposeHotReloadExtension::class.java, target)
        target.withKotlinPlugin(target::onKotlinPluginApplied)
    }
}

private fun Project.onKotlinPluginApplied() {
    setupComposeHotReloadModelBuilder()
    setupComposeHotReloadAttributes()
    setupComposeHotReloadRuntimeElements()
    setupComposeReloadHotClasspathTasks()
    setupComposeHotReloadExecTasks()
    setupArgfileTasks()
    setupComposeHotRunConventions()
    setupComposeDevCompilation()
    setupComposeCompilations()
}
