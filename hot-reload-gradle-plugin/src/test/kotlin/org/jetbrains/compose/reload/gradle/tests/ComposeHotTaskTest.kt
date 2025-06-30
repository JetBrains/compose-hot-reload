/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

@file:OptIn(InternalHotReloadTestApi::class)

package org.jetbrains.compose.reload.gradle.tests

import org.gradle.api.Task
import org.gradle.testfixtures.ProjectBuilder
import org.jetbrains.compose.reload.core.asTemplateOrThrow
import org.jetbrains.compose.reload.core.renderOrThrow
import org.jetbrains.compose.reload.core.testFixtures.sanitized
import org.jetbrains.compose.reload.gradle.ComposeHotReloadLifecycleTask
import org.jetbrains.compose.reload.gradle.ComposeHotTask
import org.jetbrains.compose.reload.gradle.ComposeHotTask.Companion.COMPOSE_HOT_RELOAD_OTHER_GROUP
import org.jetbrains.compose.reload.gradle.ComposeHotTask.Companion.COMPOSE_HOT_RELOAD_RUN_GROUP
import org.jetbrains.compose.reload.gradle.kotlinMultiplatformOrNull
import org.jetbrains.compose.reload.gradle.utils.evaluate
import org.jetbrains.compose.reload.test.core.InternalHotReloadTestApi
import org.jetbrains.compose.reload.test.core.TestEnvironment
import kotlin.io.path.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.fail

class ComposeHotTaskTest {
    @Test
    fun `test - all tasks in package implement ComposeHotTask `() {
        val ourTasks = tasksInPackage()
        if (ourTasks.isEmpty()) fail("No ComposeHotReloadTasks found")

        val violations = ourTasks.filter { task -> task !is ComposeHotTask }
        if (violations.isNotEmpty()) {
            val types = violations.map { it.javaClass.name }.toSet()
            fail(
                "Tasks ${violations.joinToString { it.name }} do not implement ComposeHotReloadTask\n" +
                    "Types: $types"
            )
        }
    }

    @Test
    fun `test- convention - task class name`() {
        val violations = mutableListOf<String>()
        tasksInPackage().forEach { task ->
            val className = task.javaClass.simpleName
            if (!className.startsWith("ComposeHot")) {
                violations += """
                    Task: ${task.name}
                    Type: ${task.javaClass.name}
                    ⚠️Class Name does not start with 'ComposeHot'
                """.trimIndent()
            }
        }

        if (violations.isEmpty()) return
        fail(
            """
            Found ${violations.size} task class name violations:
            {{violation}}
        """.trimIndent().asTemplateOrThrow().renderOrThrow {
                violations.forEach { violation ->
                    "violation"(violation)
                    "violation"("\n")
                }
            })
    }

    @Test
    fun `test - convention - task name`() {
        val violations = mutableListOf<String>()
        tasksInPackage().forEach { task ->
            if (task is ComposeHotReloadLifecycleTask) return@forEach

            /* Utility / Other tasks shall start with the 'hot' prefix */
            if (!task.name.startsWith("hot")) {
                violations.add("Task: ${task.name} does not start with 'hot' prefix")
            }
        }

        if (violations.isEmpty()) return
        fail(violations.joinToString(separator = "\n") { it })
    }

    @Test
    fun `test - convention - task group and description`() {
        val violations = mutableListOf<String>()
        tasksInPackage().forEach { task ->
            if (task.description == null || task.description?.isEmpty() == true) {
                violations.add("Task: ${task.name} does not have a description")
            }

            if (task.group !in listOf(COMPOSE_HOT_RELOAD_RUN_GROUP, COMPOSE_HOT_RELOAD_OTHER_GROUP)) {
                violations.add("Task: ${task.name} has an improper group: '${task.group}'")
            }
        }

        if (violations.isEmpty()) return
        fail(violations.joinToString(separator = "\n") { it })
    }

    @Test
    fun `test - tasks dump`() {
        val actualText = tasksInPackage()
            .sortedWith(compareBy<Task> { it.name }.thenComparing { it.javaClass.name })
            .joinToString(separator = "\n") { task ->
                "${task.name} (${task.javaClass.name
                    .removeSuffix("_Decorated")
                    .removePrefix("org.jetbrains.compose.reload.gradle.")
                })"
            }.sanitized()

        val expectFile = Path("src/test/resources/tasks/tasks-dump.txt")
        if (!expectFile.exists() || TestEnvironment.updateTestData) {
            expectFile.createParentDirectories().writeText(actualText)
            if (TestEnvironment.updateTestData) return
            else fail("Expected tasks file ${expectFile.toUri()} did not exist; Generated")
        }

        val expectText = expectFile.readText().sanitized()
        if (expectText != actualText) {
            val actualFile = expectFile.resolveSibling("tasks-dump-actual.txt")
            actualFile.writeText(actualText)
            fail("Tasks file '${expectFile.toUri()}' did not match")
        }
    }

    private fun tasksInPackage(): List<Task> {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("org.jetbrains.kotlin.multiplatform")
        project.plugins.apply("org.jetbrains.kotlin.plugin.compose")
        project.plugins.apply("org.jetbrains.compose")
        project.plugins.apply("org.jetbrains.compose.hot-reload")

        project.kotlinMultiplatformOrNull?.jvm()
        project.evaluate()
        return project.tasks.filter { it.javaClass.packageName.startsWith("org.jetbrains.compose.reload.gradle") }
    }
}
