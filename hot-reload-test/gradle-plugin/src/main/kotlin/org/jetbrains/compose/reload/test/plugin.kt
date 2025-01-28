package org.jetbrains.compose.reload.test

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.compose.reload.gradle.withKotlinPlugin

@Suppress("unused")
class HotReloadTestPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.withKotlinPlugin {
            target.configureHotReloadTestTasks()
        }
    }
}
