/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload

import org.gradle.api.Project
import org.gradle.kotlin.dsl.withType
import org.jetbrains.compose.reload.gradle.withComposeCompilerPlugin
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

internal fun Project.setupComposeCompilations() {
    withComposeCompilerPlugin {
        tasks.withType<KotlinJvmCompile>().configureEach { task ->
            if (isIdeaSync.orNull == true) return@configureEach

            // TODO: Use pluginOptions instead
            task.compilerOptions.freeCompilerArgs.addAll(
                "-P", "plugin:androidx.compose.compiler.plugins.kotlin:generateFunctionKeyMetaAnnotations=true"
            )
        }
    }
}
