/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.gradle

import org.gradle.api.Project
import org.jetbrains.compose.ComposePlugin
import org.jetbrains.kotlin.gradle.plugin.KotlinPluginWrapper


/**
 * Preliminary check if the 'org.jetbrains.compose' plugin is available.
 * If the plugin is inaccessible (not loaded? Loaded wrongly?), then we'll issue a warning and hot reload
 * is supposed to be disabled.
 */
internal fun Project.hasComposePluginAccess(): Boolean {
    return try {
        check(
            Class.forName(
                ComposePlugin::class.java.name, false, ComposeHotReloadPlugin::class.java.classLoader
            ) == ComposePlugin::class.java
        )
        true
    } catch (_: Throwable) {
        logger.error(ErrorMessages.inaccessibleComposePlugin(project))
        false
    }
}

/**
 * Preliminary check if the 'org.jetbrains.compose' plugin is available.
 * If the plugin is inaccessible (not loaded? Loaded wrongly?), then we'll issue a warning and hot reload
 * is supposed to be disabled.
 */
internal fun Project.hasKotlinPluginAccess(): Boolean {
    return try {
        check(
            Class.forName(
                KotlinPluginWrapper::class.java.name, false, ComposeHotReloadPlugin::class.java.classLoader
            ) == KotlinPluginWrapper::class.java
        )
        true
    } catch (_: Throwable) {
        logger.error(ErrorMessages.inaccessibleKotlinPlugin(project))
        false
    }
}
