package org.jetbrains.compose.reload

import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import java.util.*

internal val String.capitalized
    get() = this.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

internal val Project.kotlinMultiplatformOrNull: KotlinMultiplatformExtension?
    get() = extensions.getByName("kotlin") as? KotlinMultiplatformExtension

internal val Project.kotlinJvmOrNull: KotlinJvmProjectExtension?
    get() = extensions.getByName("kotlin") as? KotlinJvmProjectExtension

internal fun Project.files(lazy: () -> Any) = files({ lazy() })

internal fun Project.withComposePlugin(block: () -> Unit) {
    pluginManager.withPlugin("org.jetbrains.compose") {
        block()
    }
}

internal fun Project.withComposeCompilerPlugin(block: () -> Unit) {
    pluginManager.withPlugin("org.jetbrains.kotlin.plugin.compose") {
        block()
    }
}
