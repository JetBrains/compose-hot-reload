/*
 * Copyright 2024-2026 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.gradle.tests.android

import org.jetbrains.compose.reload.gradle.utils.AndroidProjectKind
import org.jetbrains.compose.reload.gradle.utils.androidProject
import org.jetbrains.compose.reload.gradle.utils.evaluate
import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.compose.reload.core.HOT_RELOAD_VERSION
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The Android dev runtime ('hot-reload-runtime-android') is part of the 'composeHotReload' build type only:
 * it is declared regardless of the requested tasks, and never lands in 'debug' or 'release'.
 */
class AndroidRuntimeDependenciesTest {
    @Test
    fun `test - kmp`() {
        val project = androidProject(AndroidProjectKind.Kmp)
        project.evaluate()

        assertEquals(setOf("androidComposeHotReloadImplementation"), project.configurationsDeclaringRuntimeAndroid())
    }

    @Test
    fun `test - kotlin android`() {
        val project = androidProject(AndroidProjectKind.KotlinAndroid)
        project.evaluate()

        assertEquals(setOf("composeHotReloadImplementation"), project.configurationsDeclaringRuntimeAndroid())
    }

    @Test
    fun `test - declared regardless of the requested tasks`() {
        listOf(emptyList(), listOf("assembleDebug"), listOf("installDebug"), listOf("reload")).forEach { requested ->
            val project = androidProject(AndroidProjectKind.Kmp, requestedTasks = requested)
            project.evaluate()

            assertEquals(
                setOf("androidComposeHotReloadImplementation"), project.configurationsDeclaringRuntimeAndroid(),
                "The runtime is part of the hot variant, not of the invocation: $requested"
            )
        }
    }

    @Test
    fun `test - foreign build type ending in the hot name stays cold`() {
        val project = androidProject(AndroidProjectKind.Kmp)
        project.extensions.configure<ApplicationExtension> {
            buildTypes.create("demoComposeHotReload") { it.initWith(buildTypes.getByName("debug")) }
        }
        project.evaluate()

        assertEquals(setOf("androidComposeHotReloadImplementation"), project.configurationsDeclaringRuntimeAndroid())
    }

    @Test
    fun `test - library`() {
        val project = androidProject(AndroidProjectKind.Kmp, application = false)
        project.evaluate()

        assertEquals(emptySet(), project.configurationsDeclaringRuntimeAndroid())
    }

    @Test
    fun `test - runtime version`() {
        val project = androidProject(AndroidProjectKind.Kmp)
        project.evaluate()

        assertEquals(
            setOf(HOT_RELOAD_VERSION),
            project.configurations.getByName("androidComposeHotReloadImplementation").dependencies
                .filter { it.name == "hot-reload-runtime-android" }
                .map { it.version }.toSet()
        )
    }

    /*
    Only 'declarable' configurations (e.g. 'composeHotReloadImplementation'): AGP itself copies a variant's
    declared dependencies into the resolvable classpaths of its test components.
     */
    private fun Project.configurationsDeclaringRuntimeAndroid(): Set<String> =
        configurations.filter { !it.isCanBeResolved && !it.isCanBeConsumed }.filter { configuration ->
            configuration.dependencies.any { dependency ->
                dependency.group == "org.jetbrains.compose.hot-reload" && dependency.name == "hot-reload-runtime-android"
            }
        }.map { it.name }.toSet()
}
