/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.gradle.tests

import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.gradle.testfixtures.ProjectBuilder
import org.jetbrains.compose.ComposeExtension
import org.jetbrains.compose.ComposePlugin
import org.jetbrains.compose.desktop.DesktopExtension
import org.jetbrains.compose.reload.gradle.AbstractComposeHotRun
import org.jetbrains.compose.reload.gradle.ComposeHotReloadPlugin
import org.jetbrains.compose.reload.gradle.ComposeHotRun
import org.jetbrains.compose.reload.gradle.kotlinMultiplatformOrNull
import org.jetbrains.compose.reload.gradle.utils.evaluate
import org.jetbrains.kotlin.gradle.plugin.KotlinMultiplatformPluginWrapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class ComposeHotRunTasksTest {
    @Test
    fun `test - default run task name - jvm`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("org.jetbrains.kotlin.jvm")
        project.plugins.apply("org.jetbrains.compose.hot-reload")
        project.evaluate()

        assertEquals(
            setOf("hotRun", "hotDev"), project.tasks.withType<AbstractComposeHotRun>().map { it.name }.toSet()
        )
    }

    @Test
    fun `test - default run task name - kmp`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("org.jetbrains.kotlin.multiplatform")
        project.plugins.apply("org.jetbrains.compose.hot-reload")

        project.kotlinMultiplatformOrNull?.jvm()

        project.evaluate()

        assertEquals(
            setOf("hotRunJvm", "hotDevJvm"), project.tasks.withType<AbstractComposeHotRun>().map { it.name }.toSet()
        )
    }

    @Test
    fun `test - mainClass`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply(KotlinMultiplatformPluginWrapper::class.java)
        project.plugins.apply(ComposePlugin::class.java)
        project.plugins.apply(ComposeHotReloadPlugin::class.java)
        project.plugins.apply("org.jetbrains.kotlin.plugin.compose")

        project.kotlinMultiplatformOrNull?.jvm()

        project.extensions.configure<ComposeExtension> {
            this.extensions.configure<DesktopExtension> {
                application { application ->
                    application.mainClass = "Foo"
                }
            }
        }

        project.evaluate()
        val runTasks = project.tasks.withType<ComposeHotRun>().toList()
        if (runTasks.isEmpty()) fail("Missing run tasks")

        runTasks.forEach { runTask ->
            assertEquals("Foo", runTask.mainClass.get())
        }
    }
}
