/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.gradle

import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import java.util.Locale

@InternalHotReloadGradleApi
val String.capitalized
    get() = this.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

@InternalHotReloadGradleApi
val String.decapitalized
    get() = this.replaceFirstChar { if (it.isUpperCase()) it.lowercase(Locale.getDefault()) else it.toString() }

@InternalHotReloadGradleApi
fun camelCase(vararg nameParts: String?): String =
    nameParts.filterNotNull().filter { it.isNotBlank() }.joinToString("") { it.capitalized }.decapitalized

@InternalHotReloadGradleApi
val Project.kotlinMultiplatformOrNull: KotlinMultiplatformExtension?
    get() = extensions.getByName("kotlin") as? KotlinMultiplatformExtension

@InternalHotReloadGradleApi
val Project.kotlinJvmOrNull: KotlinJvmProjectExtension?
    get() = extensions.getByName("kotlin") as? KotlinJvmProjectExtension

@InternalHotReloadGradleApi
fun Project.files(lazy: () -> Any) = files({ lazy() })

@InternalHotReloadGradleApi
fun Project.withComposePlugin(block: () -> Unit) {
    pluginManager.withPlugin("org.jetbrains.compose") {
        block()
    }
}

@InternalHotReloadGradleApi
fun Project.withComposeCompilerPlugin(block: () -> Unit) {
    pluginManager.withPlugin("org.jetbrains.kotlin.plugin.compose") {
        block()
    }
}

@InternalHotReloadGradleApi
fun Project.withKotlinPlugin(block: () -> Unit) {
    pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
        block()
    }

    pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
        block()
    }
}
