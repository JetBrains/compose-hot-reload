/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.build

import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.jetbrains.compose.reload.gradle.withKotlinPlugin
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension

val isCI = System.getenv("CI") != null ||  System.getenv("TEAMCITY_VERSION") != null

internal val Project.versionCatalogs get() = extensions.getByType(VersionCatalogsExtension::class.java)

private val localProject = ThreadLocal<Project>()

internal fun withProject(action: Project.() -> Unit) {
    (localProject.get() ?: error("No project is set in the current thread")).action()
}

internal fun withProject(project: Project, action: Project.() -> Unit) {
    localProject.set(project)
    try {
        project.action()
    } finally {
        localProject.remove()
    }
}

internal fun configureKotlin(action: KotlinProjectExtension.() -> Unit) = withProject {
    project.withKotlinPlugin {
        project.extensions.configure<KotlinProjectExtension> {
            action()
        }
    }
}
