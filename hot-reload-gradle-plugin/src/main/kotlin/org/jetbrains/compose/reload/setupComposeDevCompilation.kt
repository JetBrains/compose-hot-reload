/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload

import org.gradle.api.Project
import org.gradle.kotlin.dsl.withType
import org.jetbrains.compose.ComposePlugin
import org.jetbrains.compose.reload.core.HOT_RELOAD_VERSION
import org.jetbrains.compose.reload.gradle.core.composeReloadAutoRuntimeDependenciesEnabled
import org.jetbrains.compose.reload.gradle.kotlinJvmOrNull
import org.jetbrains.compose.reload.gradle.kotlinMultiplatformOrNull
import org.jetbrains.compose.reload.gradle.withComposePlugin
import org.jetbrains.kotlin.gradle.ExternalKotlinTargetApi
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget

internal fun Project.setupComposeDevCompilation() {
    kotlinMultiplatformOrNull?.targets?.withType<KotlinJvmTarget>()?.configureEach { target ->
        target.setupComposeDevCompilation()
    }

    kotlinJvmOrNull?.target?.setupComposeDevCompilation()
}

@OptIn(ExternalKotlinTargetApi::class)
private fun KotlinTarget.setupComposeDevCompilation() {
    val main = compilations.getByName("main")
    val dev = compilations.maybeCreate("dev")
    dev.associateWith(main)

    dev.defaultSourceSet.dependencies {
        if (project.composeReloadAutoRuntimeDependenciesEnabled) {
            implementation("org.jetbrains.compose.hot-reload:hot-reload-runtime-api:$HOT_RELOAD_VERSION")
        }

        project.withComposePlugin {
            implementation(ComposePlugin.Dependencies(project).desktop.currentOs)
        }
    }

    project.tasks.register(
        "${targetName}RunDev".replaceFirstChar { it.lowercase() }, ComposeDevRun::class.java
    ) { task ->
        task.compilation.set(dev)
    }
}
