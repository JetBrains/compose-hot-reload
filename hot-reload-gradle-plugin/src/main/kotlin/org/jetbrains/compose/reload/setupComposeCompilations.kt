package org.jetbrains.compose.reload

import org.gradle.api.Project
import org.gradle.kotlin.dsl.withType
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
