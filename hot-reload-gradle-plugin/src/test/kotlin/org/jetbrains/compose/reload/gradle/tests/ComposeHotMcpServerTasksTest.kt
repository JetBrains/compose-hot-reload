/*
 * Copyright 2024-2026 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.gradle.tests

import org.gradle.kotlin.dsl.withType
import org.gradle.testfixtures.ProjectBuilder
import org.jetbrains.compose.reload.gradle.ComposeHotMcpServer
import org.jetbrains.compose.reload.gradle.kotlinMultiplatformOrNull
import org.jetbrains.compose.reload.gradle.utils.evaluate
import kotlin.test.Test
import kotlin.test.assertEquals

class ComposeHotMcpServerTasksTest {
    @Test
    fun `test - mcp server task name - jvm`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("org.jetbrains.kotlin.jvm")
        project.plugins.apply("org.jetbrains.compose.hot-reload")
        project.evaluate()

        assertEquals(
            setOf("hotMcpServer"),
            project.tasks.withType<ComposeHotMcpServer>().map { it.name }.toSet()
        )
    }

    @Test
    fun `test - mcp server task name - kmp`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("org.jetbrains.kotlin.multiplatform")
        project.plugins.apply("org.jetbrains.compose.hot-reload")

        project.kotlinMultiplatformOrNull?.jvm()

        project.evaluate()

        assertEquals(
            setOf("hotMcpServerJvm"),
            project.tasks.withType<ComposeHotMcpServer>().map { it.name }.toSet()
        )
    }
}
