/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
@file:JvmName("ComposeHotReloadAgent")

package org.jetbrains.compose.reload.gradle

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.attributes.Bundling
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.Usage
import org.gradle.api.file.FileCollection
import org.gradle.kotlin.dsl.exclude
import org.gradle.kotlin.dsl.named
import org.jetbrains.compose.reload.InternalHotReloadApi
import org.jetbrains.compose.reload.core.HOT_RELOAD_VERSION
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

@InternalHotReloadApi
internal val Project.composeHotReloadAgentConfiguration: Configuration
    get() {
        configurations.findByName(HOT_RELOAD_AGENT_CONFIGURATION_NAME)?.let { return it }
        return configurations.create(HOT_RELOAD_AGENT_CONFIGURATION_NAME) { configuration ->
            configuration.isCanBeConsumed = false
            configuration.isCanBeResolved = true

            configuration.attributes.apply {
                attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage.JAVA_RUNTIME))
                attribute(Category.CATEGORY_ATTRIBUTE, project.objects.named(Category.LIBRARY))
                attribute(KotlinPlatformType.attribute, KotlinPlatformType.jvm)
                attribute(Bundling.BUNDLING_ATTRIBUTE, project.objects.named(Bundling.EXTERNAL))
            }

            configuration.dependencies.add(
                project.dependencies.create("org.jetbrains.compose.hot-reload:hot-reload-agent:$HOT_RELOAD_VERSION")
            )

            /**
             * We're resolving the agent classpath as part of the System ClassLoader for applications.
             * This classpath will also resolve in 'isolation' and will be put into the leading position
             * of the classpath. Since we resolve a 'regular' (not shadowed) classpath, we ensure that
             * we do not include the kotlin-stdlib from this classpath:
             * We expect the user application to provide a kotlin-stdlib for us.
             */
            configuration.exclude("org.jetbrains.kotlin", "kotlin-stdlib")
        }
    }

/**
 * Provides the 'default' 'Compose Hot Reload' agent jar file.
 */
val Project.composeHotReloadAgentJar: FileCollection
    get() = composeHotReloadAgentConfiguration.incoming.artifactView { view ->
        view.componentFilter { element ->
            element is ModuleComponentIdentifier &&
                (element.group == "org.jetbrains.compose.hot-reload" && element.module == "hot-reload-agent") ||
                (element is ProjectComponentIdentifier && element.projectPath == ":hot-reload-agent")
        }
    }.files

/**
 * Resolves the classpath required for the default 'Compose Hot Reload
 * Agent'
 */
val Project.composeHotReloadAgentClasspath: FileCollection
    get() = composeHotReloadAgentConfiguration.incoming.files
