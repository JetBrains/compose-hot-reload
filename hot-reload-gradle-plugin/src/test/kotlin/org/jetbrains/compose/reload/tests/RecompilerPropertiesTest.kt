package org.jetbrains.compose.reload.tests

import org.gradle.kotlin.dsl.create
import org.gradle.testfixtures.ProjectBuilder
import org.jetbrains.compose.reload.ComposeHotRun
import org.jetbrains.compose.reload.core.HotReloadProperty.*
import org.jetbrains.compose.reload.kotlinMultiplatformOrNull
import org.jetbrains.compose.reload.utils.withRepositories
import kotlin.test.Test
import kotlin.test.assertEquals

class RecompilerPropertiesTest {
    @Test
    fun `test - root project`() {
        val project = ProjectBuilder.builder().build()
        project.withRepositories()


        project.plugins.apply("org.jetbrains.kotlin.multiplatform")
        project.plugins.apply("org.jetbrains.compose-hot-reload")
        project.kotlinMultiplatformOrNull?.jvm()

        val hotRun = project.tasks.create<ComposeHotRun>("runHot")

        assertEquals(project.rootDir.absolutePath, hotRun.systemProperties[ComposeBuildRoot.key])
        assertEquals(":", hotRun.systemProperties[ComposeBuildProject.key])
        assertEquals("reloadJvmMainClasspath", hotRun.systemProperties[ComposeBuildTask.key].toString())
    }

    @Test
    fun `test - subproject`() {
        val project = ProjectBuilder.builder().build()
        val subproject = ProjectBuilder.builder().withParent(project).withName("foo").build()
        subproject.withRepositories()


        subproject.plugins.apply("org.jetbrains.kotlin.multiplatform")
        subproject.plugins.apply("org.jetbrains.compose-hot-reload")
        subproject.kotlinMultiplatformOrNull?.jvm()

        val hotRun = subproject.tasks.create<ComposeHotRun>("runHot")

        assertEquals(subproject.rootDir.absolutePath, hotRun.systemProperties[ComposeBuildRoot.key])
        assertEquals(":foo", hotRun.systemProperties[ComposeBuildProject.key])
        assertEquals("reloadJvmMainClasspath", hotRun.systemProperties[ComposeBuildTask.key].toString())
    }
}
