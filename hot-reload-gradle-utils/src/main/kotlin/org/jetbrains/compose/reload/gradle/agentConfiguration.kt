package org.jetbrains.compose.reload.gradle

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.file.FileCollection
import org.jetbrains.compose.reload.core.HOT_RELOAD_VERSION

private const val composeHotReloadAgentConfigurationName = "composeHotReloadAgent"

@InternalHotReloadGradleApi
val Project.composeHotReloadAgentConfiguration: Configuration
    get() {
        configurations.findByName(composeHotReloadAgentConfigurationName)?.let { return it }
        return configurations.create(composeHotReloadAgentConfigurationName) { configuration ->
            configuration.isCanBeConsumed = false
            configuration.isCanBeResolved = true

            configuration.dependencies.add(
                project.dependencies.create("org.jetbrains.compose:hot-reload-agent:$HOT_RELOAD_VERSION")
            )
        }
    }

@InternalHotReloadGradleApi
fun Project.composeHotReloadAgentJar(): FileCollection {
    return composeHotReloadAgentConfiguration.incoming.artifactView { view ->
        view.componentFilter { element ->
            element is ModuleComponentIdentifier && element.group == "org.jetbrains.compose" && element.module == "hot-reload-agent"
        }
    }.files
}

@InternalHotReloadGradleApi
fun Project.composeHotReloadAgentRuntimeClasspath(): FileCollection {
    return composeHotReloadAgentConfiguration.incoming.files
}
