/*
 * Copyright 2024-2026 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.gradle.android

import org.gradle.api.Project
import org.jetbrains.compose.reload.core.HOT_RELOAD_VERSION
import org.jetbrains.compose.reload.gradle.Future
import org.jetbrains.compose.reload.gradle.composeReloadAutoRuntimeDependenciesEnabled
import org.jetbrains.compose.reload.gradle.forAllAndroidCompilations
import org.jetbrains.compose.reload.gradle.projectFuture

/**
 * Adds the `hot-reload-runtime-android` dev runtime to dependencies so consumers don't have to declare it.
 * The AAR self-initializes via a manifest-merged `ContentProvider`, so no app code is needed.
 *
 * The runtime is part of the [hotAndroidBuildTypeName] variant only — it never touches 'debug' or
 * 'release', so a regular build stays exactly what the app's own build produces.
 */
internal val Project.hotAndroidRuntimeDependencies: Future<Unit> by projectFuture {
    if (!composeReloadAutoRuntimeDependenciesEnabled) return@projectFuture

    forAllAndroidCompilations { compilation ->
        if (!compilation.isHotVariant()) return@forAllAndroidCompilations

        compilation.defaultSourceSet.dependencies {
            implementation("org.jetbrains.compose.hot-reload:hot-reload-runtime-android:$HOT_RELOAD_VERSION")
        }
    }

    Unit
}
