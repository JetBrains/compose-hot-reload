/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.gradle

import org.gradle.api.Project
import org.jetbrains.compose.reload.core.HOT_RELOAD_COMPOSE_VERSION
import org.jetbrains.compose.reload.core.HOT_RELOAD_KOTLIN_VERSION
import org.jetbrains.compose.reload.core.HOT_RELOAD_VERSION

internal object ErrorMessages {
    fun missingMainClassProperty(taskName: String) = """
        Missing 'mainClass' property. Please invoke the task with '-PmainClass=...`
        Example: ./gradlew $taskName -PmainClass=my.package.MainKt
    """.trimIndent()

    fun inaccessibleComposePlugin(project: Project) = """
        ${project.displayName}: Cannot access 'org.jetbrains.compose' plugin. 
        Was this plugin loaded?
        ```
            plugins {
                id("org.jetbrains.compose") version "$HOT_RELOAD_COMPOSE_VERSION"
                id("org.jetbrains.compose.hot-reload") version "$HOT_RELOAD_VERSION"
            }
        ```
        """.trimIndent()

    fun inaccessibleKotlinPlugin(project: Project) = """
        ${project.displayName}: Cannot access 'org.jetbrains.kotlin.*' plugin. 
        Was this plugin loaded?
        ```
            plugins {
                id("org.jetbrains.kotlin.multiplatform") version "$HOT_RELOAD_KOTLIN_VERSION"
                id("org.jetbrains.compose.hot-reload") version "$HOT_RELOAD_VERSION"
            }
        ```
        """.trimIndent()
}
