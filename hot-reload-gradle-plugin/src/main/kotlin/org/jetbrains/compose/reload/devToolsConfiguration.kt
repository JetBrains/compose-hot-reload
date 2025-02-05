/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.Usage
import org.gradle.api.attributes.Usage.USAGE_ATTRIBUTE
import org.gradle.api.attributes.java.TargetJvmEnvironment
import org.jetbrains.compose.ComposePlugin
import org.jetbrains.compose.reload.core.HOT_RELOAD_VERSION
import org.jetbrains.compose.reload.gradle.withComposePlugin
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType


private const val composeHotReloadDevToolsConfigurationName = "composeHotReloadDevTools"

internal val Project.composeHotReloadDevToolsConfiguration: Configuration
    get() {
        configurations.findByName(composeHotReloadDevToolsConfigurationName)?.let { return it }
        return configurations.create(composeHotReloadDevToolsConfigurationName) { configuration ->
            configuration.isCanBeConsumed = false
            configuration.isCanBeResolved = true

            configuration.attributes.attribute(
                USAGE_ATTRIBUTE,
                project.objects.named(Usage::class.java, Usage.JAVA_RUNTIME)
            )

            configuration.attributes.attribute(
                Category.CATEGORY_ATTRIBUTE,
                project.objects.named(Category::class.java, Category.LIBRARY)
            )

            configuration.attributes.attribute(
                TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE,
                project.objects.named(TargetJvmEnvironment::class.java, TargetJvmEnvironment.STANDARD_JVM)
            )

            configuration.attributes.attribute(
                KotlinPlatformType.attribute, KotlinPlatformType.jvm
            )

            configuration.dependencies.add(
                project.dependencies.create("org.jetbrains.compose:hot-reload-devtools:$HOT_RELOAD_VERSION")
            )

            project.withComposePlugin {
                configuration.dependencies.add(
                    project.dependencies.create(ComposePlugin.Dependencies(project).desktop.currentOs)
                )
            }
        }
    }
