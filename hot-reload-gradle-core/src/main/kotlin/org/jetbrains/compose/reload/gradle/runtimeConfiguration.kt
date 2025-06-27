/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
@file:JvmName("ComposeHotReloadRuntime")

package org.jetbrains.compose.reload.gradle

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.Usage
import org.gradle.api.attributes.java.TargetJvmEnvironment.STANDARD_JVM
import org.gradle.api.attributes.java.TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE
import org.gradle.api.file.FileCollection
import org.gradle.kotlin.dsl.named
import org.jetbrains.compose.reload.InternalHotReloadApi
import org.jetbrains.compose.reload.core.HOT_RELOAD_VERSION
import org.jetbrains.compose.reload.gradle.HotReloadUsage.COMPOSE_DEV_RUNTIME_USAGE
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

/**
 * Contains the 'dev' variant of the 'hot reload runtime':
 * Aka. the version of the runtime which is required for running in hot reload mode.
 */
val Project.composeHotReloadRuntimeClasspath: FileCollection get() = composeHotReloadRuntimeConfiguration.incoming.files

/**
 * This configuration only contains the 'dev' variant of the 'hot-reload:runtime-*' artifacts.
 */
@InternalHotReloadApi
val Project.composeHotReloadRuntimeConfiguration: Configuration
    get() = project.configurations.findByName(HOT_RELOAD_RUNTIME_CONFIGURATION_NAME)
        ?: project.configurations.create(HOT_RELOAD_RUNTIME_CONFIGURATION_NAME) { configuration ->
            configuration.isCanBeResolved = true
            configuration.isCanBeConsumed = false
            configuration.isVisible = false

            configuration.attributes.attribute(KotlinPlatformType.attribute, KotlinPlatformType.jvm)
            configuration.attributes.attribute(Category.CATEGORY_ATTRIBUTE, project.objects.named(Category.LIBRARY))
            configuration.attributes.attribute(TARGET_JVM_ENVIRONMENT_ATTRIBUTE, project.objects.named(STANDARD_JVM))
            configuration.attributes.attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(COMPOSE_DEV_RUNTIME_USAGE))
            configuration.attributes.attribute(HotReloadUsageType.attribute, HotReloadUsageType.Dev)

            project.dependencies.add(
                configuration.name, "org.jetbrains.compose.hot-reload:hot-reload-runtime-jvm:$HOT_RELOAD_VERSION"
            )
        }
