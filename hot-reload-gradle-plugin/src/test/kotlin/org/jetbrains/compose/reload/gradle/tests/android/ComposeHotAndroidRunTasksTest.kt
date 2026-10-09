/*
 * Copyright 2024-2026 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.gradle.tests.android

import org.jetbrains.compose.reload.gradle.utils.AndroidProjectKind
import org.jetbrains.compose.reload.gradle.utils.androidProject
import org.jetbrains.compose.reload.gradle.utils.evaluate
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.compose.reload.gradle.android.ComposeHotAndroidRunTask
import kotlin.test.Test
import kotlin.test.assertEquals

class ComposeHotAndroidRunTasksTest {
    @Test
    fun `test - default run task name - kmp`() {
        val project = androidProject(AndroidProjectKind.Kmp)
        project.evaluate()

        assertEquals(setOf("hotRunAndroid"), project.tasks.withType<ComposeHotAndroidRunTask>().names)
    }

    @Test
    fun `test - default run task name - kotlin android`() {
        val project = androidProject(AndroidProjectKind.KotlinAndroid)
        project.evaluate()

        assertEquals(setOf("hotRun"), project.tasks.withType<ComposeHotAndroidRunTask>().names)
    }

    @Test
    fun `test - no run task for library`() {
        val project = androidProject(AndroidProjectKind.Kmp, application = false)
        project.evaluate()

        assertEquals(emptySet(), project.tasks.withType<ComposeHotAndroidRunTask>().names)
    }

            @Test
    fun `test - product flavors`() {
        val project = androidProject(AndroidProjectKind.Kmp)
        project.extensions.configure<ApplicationExtension> {
            flavorDimensions += "tier"
            productFlavors.create("free") { it.dimension = "tier" }
            productFlavors.create("paid") { it.dimension = "tier" }
        }
        project.evaluate()

        val runTasks = project.tasks.withType<ComposeHotAndroidRunTask>()
        assertEquals(setOf("hotRunAndroidFree", "hotRunAndroidPaid"), runTasks.names)
        runTasks.forEach { task ->
            /* The install task keeps the full variant name; the hot task name omits the build type segment */
            assertEquals(
                "install${task.name.removePrefix("hotRunAndroid")}ComposeHotReload",
                task.taskDependencies.getDependencies(task).map { it.name }.single { it.startsWith("install") }
            )
        }
    }

    @Test
    fun `test - run task dependencies`() {
        val project = androidProject(AndroidProjectKind.Kmp)
        project.evaluate()

        val runTask = project.tasks.withType<ComposeHotAndroidRunTask>().getByName("hotRunAndroid")
        assertEquals(
            setOf("installComposeHotReload", "hotSnapshotAndroid"),
            runTask.taskDependencies.getDependencies(runTask).map { it.name }.toSet()
        )
    }

    @Test
    fun `test - applicationId`() {
        val project = androidProject(AndroidProjectKind.Kmp)
        project.evaluate()

        assertEquals(
            "com.example.app",
            project.tasks.withType<ComposeHotAndroidRunTask>().getByName("hotRunAndroid").applicationId.get()
        )
    }

    @Test
    fun `test - applicationId - with build type suffix`() {
        val project = androidProject(AndroidProjectKind.Kmp)
        project.extensions.configure<ApplicationExtension> {
            buildTypes.getByName("composeHotReload").applicationIdSuffix = ".debug"
        }
        project.evaluate()

        assertEquals(
            "com.example.app.debug",
            project.tasks.withType<ComposeHotAndroidRunTask>().getByName("hotRunAndroid").applicationId.get()
        )
    }

    @Test
    fun `test - applicationId - falls back to namespace`() {
        val project = androidProject(AndroidProjectKind.Kmp)
        project.extensions.configure<ApplicationExtension> {
            defaultConfig.applicationId = null
        }
        project.evaluate()

        assertEquals(
            "com.example",
            project.tasks.withType<ComposeHotAndroidRunTask>().getByName("hotRunAndroid").applicationId.get()
        )
    }

    @Test
    fun `test - applicationId - product flavors`() {
        val project = androidProject(AndroidProjectKind.Kmp)
        project.extensions.configure<ApplicationExtension> {
            flavorDimensions += "tier"
            productFlavors.create("free") { it.dimension = "tier"; it.applicationIdSuffix = ".free" }
            productFlavors.create("paid") { it.dimension = "tier"; it.applicationId = "com.example.paid" }
            buildTypes.getByName("composeHotReload").applicationIdSuffix = ".debug"
        }
        project.evaluate()

        val runTasks = project.tasks.withType<ComposeHotAndroidRunTask>()
        assertEquals("com.example.app.free.debug", runTasks.getByName("hotRunAndroidFree").applicationId.get())
        assertEquals("com.example.paid.debug", runTasks.getByName("hotRunAndroidPaid").applicationId.get())
    }

    @Test
    fun `test - applicationId - set by the variant API`() {
        val project = androidProject(AndroidProjectKind.Kmp)
        project.extensions.getByType(ApplicationAndroidComponentsExtension::class.java).onVariants { variant ->
            variant.applicationId.set("com.example.variant")
        }
        project.evaluate()

        assertEquals(
            "com.example.variant",
            project.tasks.withType<ComposeHotAndroidRunTask>().getByName("hotRunAndroid").applicationId.get()
        )
    }
}
