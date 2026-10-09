/*
 * Copyright 2024-2026 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.gradle.tests.android

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.artifacts.component.ProjectComponentSelector
import org.gradle.api.artifacts.result.UnresolvedDependencyResult
import org.gradle.kotlin.dsl.configure
import org.gradle.testfixtures.ProjectBuilder
import org.jetbrains.compose.reload.gradle.utils.evaluate
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The hot reload build type exists only in the modules the plugin is applied to: an app's hot variant
 * must still resolve an Android library module that does not carry it (it only has 'debug' and 'release').
 */
class AndroidLibraryDependencyTest {
    @Test
    fun `test - library module without the plugin resolves`() {
        val root = ProjectBuilder.builder().build()
        val library = ProjectBuilder.builder().withName("lib").withParent(root).build()
        val app = ProjectBuilder.builder().withName("app").withParent(root).build()

        library.plugins.apply("com.android.library")
        library.extensions.configure<LibraryExtension> {
            compileSdk = 34
            namespace = "com.example.lib"
        }

        app.plugins.apply("com.android.application")
        app.plugins.apply("org.jetbrains.kotlin.android")
        app.plugins.apply("org.jetbrains.kotlin.plugin.compose")
        app.plugins.apply("org.jetbrains.compose")
        app.plugins.apply("org.jetbrains.compose.hot-reload")
        app.extensions.configure<ApplicationExtension> {
            compileSdk = 34
            namespace = "com.example"
            defaultConfig.applicationId = "com.example.app"
        }
        app.dependencies.add("implementation", app.project(":lib"))

        library.evaluate()
        app.evaluate()

        val unresolved = app.configurations.getByName("composeHotReloadRuntimeClasspath")
            .incoming.resolutionResult.allDependencies
            .filterIsInstance<UnresolvedDependencyResult>()
            .filter { it.requested is ProjectComponentSelector }

        assertEquals(
            emptyList(), unresolved.map { generateSequence<Throwable>(it.failure) { e -> e.cause }.joinToString(" <- ") { e -> e.message.orEmpty() } },
            "The library module must resolve"
        )
    }
}
