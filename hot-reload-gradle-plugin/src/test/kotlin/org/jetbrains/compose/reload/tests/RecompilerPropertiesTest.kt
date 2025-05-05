/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.tests

import org.gradle.kotlin.dsl.create
import org.gradle.testfixtures.ProjectBuilder
import org.jetbrains.compose.reload.ComposeHotRun
import org.jetbrains.compose.reload.core.HotReloadProperty.GradleBuildProject
import org.jetbrains.compose.reload.core.HotReloadProperty.GradleBuildRoot
import org.jetbrains.compose.reload.core.HotReloadProperty.GradleBuildTask
import org.jetbrains.compose.reload.core.HotReloadProperty.GradleJavaHome
import org.jetbrains.compose.reload.gradle.kotlinMultiplatformOrNull
import org.jetbrains.compose.reload.utils.assertSystemPropertyEquals
import org.jetbrains.compose.reload.utils.getComposeHotReloadArgumentsOrFail
import org.jetbrains.compose.reload.utils.withRepositories
import kotlin.test.Test

class RecompilerPropertiesTest {
    @Test
    fun `test - root project`() {
        val project = ProjectBuilder.builder().build()
        project.withRepositories()

        project.plugins.apply("org.jetbrains.kotlin.multiplatform")
        project.plugins.apply("org.jetbrains.compose.hot-reload")
        project.kotlinMultiplatformOrNull?.jvm()

        val hotRun = project.tasks.create<ComposeHotRun>("runHot")
        val arguments = hotRun.getComposeHotReloadArgumentsOrFail()

        arguments.assertSystemPropertyEquals(GradleBuildRoot, project.rootDir.absolutePath)
        arguments.assertSystemPropertyEquals(GradleBuildProject, ":")
        arguments.assertSystemPropertyEquals(GradleBuildTask, "hotReloadJvmMain")
        arguments.assertSystemPropertyEquals(GradleJavaHome, System.getProperty("java.home"))
    }

    @Test
    fun `test - subproject`() {
        val project = ProjectBuilder.builder().build()
        val subproject = ProjectBuilder.builder().withParent(project).withName("foo").build()
        subproject.withRepositories()


        subproject.plugins.apply("org.jetbrains.kotlin.multiplatform")
        subproject.plugins.apply("org.jetbrains.compose.hot-reload")
        subproject.kotlinMultiplatformOrNull?.jvm()

        val hotRun = subproject.tasks.create<ComposeHotRun>("runHot")
        val arguments = hotRun.getComposeHotReloadArgumentsOrFail()

        arguments.assertSystemPropertyEquals(GradleBuildRoot, project.rootDir.absolutePath)
        arguments.assertSystemPropertyEquals(GradleBuildProject, ":foo")
        arguments.assertSystemPropertyEquals(GradleBuildTask, "hotReloadJvmMain")
    }
}
