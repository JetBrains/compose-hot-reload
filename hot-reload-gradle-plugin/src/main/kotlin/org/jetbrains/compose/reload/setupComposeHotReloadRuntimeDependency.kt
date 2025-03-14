/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload

import org.gradle.api.Project
import org.jetbrains.compose.reload.core.HOT_RELOAD_VERSION
import org.jetbrains.compose.reload.gradle.kotlinJvmOrNull
import org.jetbrains.compose.reload.gradle.kotlinMultiplatformOrNull

internal fun Project.setupComposeHotReloadRuntimeDependency() {
    if (!project.autoRuntimeDependenciesEnabled.get()) return

    /*
    Multiplatform impl
     */
    kotlinMultiplatformOrNull?.apply {
        sourceSets.commonMain.dependencies {
            implementation("org.jetbrains.compose.hot-reload:runtime-api:$HOT_RELOAD_VERSION")
        }
    }

    /*
    Jvm impl
     */
    kotlinJvmOrNull?.apply {
        val compilation = target.compilations.getByName("main")
        compilation.defaultSourceSet.dependencies {
            implementation("org.jetbrains.compose.hot-reload:runtime-api:$HOT_RELOAD_VERSION")
        }
    }
}
