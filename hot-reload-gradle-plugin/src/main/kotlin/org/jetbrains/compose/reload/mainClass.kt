/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload

import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.compose.ComposeExtension
import org.jetbrains.compose.desktop.DesktopExtension
import org.jetbrains.compose.reload.gradle.lazyProjectProperty

/**
 * Returns the 'convention'/'default' mainClass, respecting the following precedence:
 * 1) 'mainClass' Gradle Property
 * 2) 'mainClass' System Property
 * 3) 'compose.desktop.application.mainClass' (from the compose plugin)
 */
internal val Project.mainClassConvention: Provider<String> by lazyProjectProperty {
    providers.gradleProperty("mainClass")
        .orElse(providers.systemProperty("mainClass"))
        .orElse(composeDesktopApplicationMainClass)
}

private val Project.composeDesktopApplicationMainClass: Provider<String>
    get() = project.provider {
        try {
            val compose = project.extensions.findByName("compose") as? ComposeExtension ?: return@provider null
            val desktop = compose.extensions.findByName("desktop") as? DesktopExtension ?: return@provider null
            desktop.application.mainClass
        } catch (_: LinkageError) {
            /* Compose Plugin not available */
            null
        }
    }
