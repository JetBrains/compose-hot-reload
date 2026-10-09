/*
 * Copyright 2024-2026 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.gradle.utils

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.testfixtures.ProjectBuilder
import org.jetbrains.compose.reload.gradle.kotlinMultiplatformOrNull
import java.io.File

enum class AndroidProjectKind {
    /** Kotlin Multiplatform module with `jvm()` and `androidTarget()` */
    Kmp,

    /** Classic single-platform module using the `org.jetbrains.kotlin.android` plugin */
    KotlinAndroid,
}

/**
 * Creates (but does not evaluate) a Compose project with an Android application (or library) module
 * and the hot reload plugin applied.
 *
 * @param requestedTasks the task names of the simulated Gradle invocation (decides the Android 'hot mode')
 */
fun androidProject(
    kind: AndroidProjectKind = AndroidProjectKind.Kmp,
    application: Boolean = true,
    requestedTasks: List<String> = emptyList(),
    projectDir: File? = null,
): Project {
    val project = ProjectBuilder.builder().apply { if (projectDir != null) withProjectDir(projectDir) }.build()
    project.gradle.startParameter.setTaskNames(requestedTasks)

    if (kind == AndroidProjectKind.Kmp) project.plugins.apply("org.jetbrains.kotlin.multiplatform")
    project.plugins.apply(if (application) "com.android.application" else "com.android.library")
    if (kind == AndroidProjectKind.KotlinAndroid) project.plugins.apply("org.jetbrains.kotlin.android")
    project.plugins.apply("org.jetbrains.kotlin.plugin.compose")
    project.plugins.apply("org.jetbrains.compose")
    project.plugins.apply("org.jetbrains.compose.hot-reload")

    project.kotlinMultiplatformOrNull?.apply {
        jvm()
        androidTarget()
    }

    if (application) {
        project.extensions.configure<ApplicationExtension> {
            compileSdk = 34
            namespace = "com.example"
            defaultConfig.applicationId = "com.example.app"
        }
    } else {
        project.extensions.configure<LibraryExtension> {
            compileSdk = 34
            namespace = "com.example.lib"
        }
    }

    return project
}
