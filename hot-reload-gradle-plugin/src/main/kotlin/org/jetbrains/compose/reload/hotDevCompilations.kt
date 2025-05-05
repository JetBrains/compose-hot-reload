/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload

import org.gradle.api.Project
import org.jetbrains.compose.ComposePlugin
import org.jetbrains.compose.reload.core.HOT_RELOAD_VERSION
import org.jetbrains.compose.reload.gradle.Future
import org.jetbrains.compose.reload.gradle.PluginStage
import org.jetbrains.compose.reload.gradle.await
import org.jetbrains.compose.reload.gradle.awaitKotlinPlugin
import org.jetbrains.compose.reload.gradle.core.composeReloadAutoRuntimeDependenciesEnabled
import org.jetbrains.compose.reload.gradle.forAllJvmTargets
import org.jetbrains.compose.reload.gradle.future
import org.jetbrains.compose.reload.gradle.projectFuture
import org.jetbrains.compose.reload.gradle.withComposePlugin
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget

internal val Project.hotDevCompilations: Future<List<KotlinCompilation<*>>> by projectFuture {
    forAllJvmTargets { target -> target.hotDevCompilation }.map { it.await() }
}

internal val KotlinTarget.hotDevCompilation: Future<KotlinCompilation<*>> by future {
    PluginStage.EagerConfiguration.await()
    awaitKotlinPlugin()

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

    dev
}
