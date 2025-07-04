/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.gradle

import org.gradle.api.Project

/* Configure resource accessors generation of Compose Gradle Plugin */
internal fun Project.configureResourceAccessorsGenerationTasks() {
    try {
        val resourceAccessorsConfigurationClass =
            Class.forName("org.jetbrains.compose.resources.ResourceAccessorsConfiguration")
        tasks.configureEach { task ->
            if (resourceAccessorsConfigurationClass.isInstance(task)) {
                task.setProperty("generateResourceContentHashAnnotation", true)
            }
        }
    } catch (_: ClassNotFoundException) {
        // Ignore as the configuration is introduced since CMP 1.10 only
    }
}