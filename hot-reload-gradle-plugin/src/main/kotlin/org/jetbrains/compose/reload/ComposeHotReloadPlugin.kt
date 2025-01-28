package org.jetbrains.compose.reload

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.compose.reload.gradle.withKotlinPlugin
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension

@Suppress("unused")
class ComposeHotReloadPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.extensions.create(composeHotReloadExtensionName, ComposeHotReloadExtension::class.java, target)
        target.withKotlinPlugin(target::onKotlinPluginApplied)
    }
}

private fun Project.onKotlinPluginApplied() {
    /* Test to find 'Isolated Classpath' issues */
    try {
        assert(project.extensions.getByName("kotlin") is KotlinProjectExtension)
    } catch (t: LinkageError) {
        throw IllegalStateException("'Inaccessible Kotlin Plugin'")
    }

    setupComposeHotReloadRuntimeDependency()
    setupComposeHotReloadRuntimeElements()
    setupComposeReloadHotClasspathTasks()
    setupComposeHotReloadExecTasks()
    setupComposeHotRunConventions()
    setupComposeDevCompilation()
    setupComposeCompilations()
}
