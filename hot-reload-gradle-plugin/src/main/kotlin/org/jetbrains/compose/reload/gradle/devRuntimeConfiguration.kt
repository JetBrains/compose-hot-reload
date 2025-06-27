/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

@file:Suppress("UnstableApiUsage")

package org.jetbrains.compose.reload.gradle

import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.Usage
import org.gradle.api.attributes.java.TargetJvmEnvironment.STANDARD_JVM
import org.gradle.api.attributes.java.TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE
import org.gradle.kotlin.dsl.named
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.tooling.core.extrasLazyProperty


/**
 * We're trying to resolve the classpath to classes dirs:
 * Therefore, we're explicitly trying to resolve classes by looking out for a
 * [ComposeHotReloadMarker].
 *
 * Projects which also do have the hot-reload plugin applied will be able to provide
 * us with a better variant. Projects which do not have this plugin applied will provide
 * use with a regular variant.
 */
internal val KotlinCompilation<*>.composeDevRuntimeDependencies: Configuration by extrasLazyProperty("composeDevRuntimeDependencies") {
    val runtimeConfigurationName = runtimeDependencyConfigurationName ?: compileDependencyConfigurationName
    val runtimeConfiguration = project.configurations.getByName(runtimeConfigurationName)

    project.configurations.create(hotReloadDevRuntimeDependenciesConfigurationName(runtimeConfigurationName)).apply {
        /**
         * Extend from the regular 'runtimeConfiguration' as well as the 'hotReloadRuntime' which will
         * bring in additional runtime artifacts required by hot-reload
         */
        extendsFrom(runtimeConfiguration)
        extendsFrom(project.composeHotReloadRuntimeConfiguration)

        isCanBeResolved = true
        isCanBeConsumed = false
        attributes.attribute(KotlinPlatformType.attribute, KotlinPlatformType.jvm)
        attributes.attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(HotReloadUsage.COMPOSE_DEV_RUNTIME_USAGE))
        attributes.attribute(Category.CATEGORY_ATTRIBUTE, project.objects.named(Category.LIBRARY))
        attributes.attribute(TARGET_JVM_ENVIRONMENT_ATTRIBUTE, project.objects.named(STANDARD_JVM))
        attributes.attribute(HotReloadUsageType.attribute, HotReloadUsageType.Dev)
    }
}
