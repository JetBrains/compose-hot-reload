/*
 * Copyright 2024-2026 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.gradle.tests.android

import org.jetbrains.compose.reload.gradle.utils.AndroidProjectKind
import org.jetbrains.compose.reload.gradle.utils.androidProject
import org.jetbrains.compose.reload.gradle.utils.evaluate
import org.gradle.api.Task
import org.gradle.kotlin.dsl.withType
import org.jetbrains.compose.reload.core.testFixtures.sanitized
import org.jetbrains.compose.reload.gradle.ComposeHotTask
import org.jetbrains.compose.reload.gradle.ComposeHotTask.Companion.COMPOSE_HOT_RELOAD_OTHER_GROUP
import org.jetbrains.compose.reload.gradle.ComposeHotTask.Companion.COMPOSE_HOT_RELOAD_RUN_GROUP
import org.jetbrains.compose.reload.gradle.android.ComposeHotAndroidRunTask
import org.jetbrains.compose.reload.test.core.InternalHotReloadTestApi
import org.jetbrains.compose.reload.test.core.TestEnvironment
import kotlin.io.path.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

@OptIn(InternalHotReloadTestApi::class)
class ComposeHotAndroidTaskTest {
    @Test
    fun `test - all android tasks implement ComposeHotTask`() {
        val violations = androidTasks().filter { task -> task !is ComposeHotTask }
        if (violations.isNotEmpty()) {
            fail(
                "Tasks ${violations.joinToString { it.name }} do not implement ComposeHotTask\n" +
                    "Types: ${violations.map { it.javaClass.name.removeSuffix("_Decorated") }.toSet()}"
            )
        }
    }

    @Test
    fun `test - convention - task class name`() {
        val violations = androidTasks().filter { !it.javaClass.simpleName.startsWith("ComposeHot") }
        if (violations.isNotEmpty()) fail("Class names do not start with 'ComposeHot': ${violations.map { it.name }}")
    }

    @Test
    fun `test - convention - task name`() {
        val violations = androidTasks().filter { !it.name.startsWith("hot") }
        if (violations.isNotEmpty()) fail("Task names do not start with 'hot': ${violations.map { it.name }}")
    }

    @Test
    fun `test - convention - task group and description`() {
        val violations = mutableListOf<String>()
        androidTasks().forEach { task ->
            if (task.description.isNullOrEmpty()) {
                violations.add("Task: ${task.name} does not have a description")
            }

            if (task.group !in listOf(COMPOSE_HOT_RELOAD_RUN_GROUP, COMPOSE_HOT_RELOAD_OTHER_GROUP)) {
                violations.add("Task: ${task.name} has an improper group: '${task.group}'")
            }
        }

        if (violations.isNotEmpty()) fail(violations.joinToString(separator = "\n"))
    }

    @Test
    fun `test - run tasks have resolvable dependencies`() {
        val project = androidProject(AndroidProjectKind.Kmp)
        project.evaluate()

        val violations = project.tasks.withType<ComposeHotAndroidRunTask>().mapNotNull { task ->
            runCatching { task.taskDependencies.getDependencies(task) }.exceptionOrNull()?.let { error ->
                "Task: ${task.name}: ${generateSequence(error) { it.cause }.last().message}"
            }
        }

        if (violations.isNotEmpty()) fail(violations.joinToString(separator = "\n"))
    }

    @Test
    fun `test - tasks dump`() {
        val actualText = androidTasks()
            .sortedWith(compareBy<Task> { it.name }.thenComparing { it.javaClass.name })
            .joinToString(separator = "\n") { task ->
                "${task.name} (${task.javaClass.name
                    .removeSuffix("_Decorated")
                    .removePrefix("org.jetbrains.compose.reload.gradle.")
                })"
            }.sanitized()

        val expectFile = Path("src/test/resources/tasks/tasks-dump-android.txt")
        if (!expectFile.exists() || TestEnvironment.updateTestData) {
            expectFile.createParentDirectories().writeText(actualText)
            if (TestEnvironment.updateTestData) return
            else fail("Expected tasks file ${expectFile.toUri()} did not exist; Generated")
        }

        val expectText = expectFile.readText().sanitized()
        if (expectText != actualText) {
            val actualFile = expectFile.resolveSibling("tasks-dump-android-actual.txt")
            actualFile.writeText(actualText)
            fail("Tasks file '${expectFile.toUri()}' did not match")
        }
    }

    @Test
    fun `test - lazy realization of android hot reload tasks`() {
        val project = androidProject(AndroidProjectKind.Kmp)

        val realizedDuringConfiguration = mutableSetOf<String>()
        project.tasks.configureEach { task ->
            if (task.isAndroidHotTask()) realizedDuringConfiguration.add(task.name)
        }
        assertTrue(
            realizedDuringConfiguration.isEmpty(),
            "Tasks were realized during configuration:\n$realizedDuringConfiguration"
        )

        project.evaluate()

        val allAndroidHotTasks = project.tasks.filter { it.isAndroidHotTask() }
        assertTrue(allAndroidHotTasks.isNotEmpty(), "Android hot reload tasks were not realized")
    }

    private fun Task.isAndroidHotTask(): Boolean =
        javaClass.packageName.startsWith("org.jetbrains.compose.reload.gradle.android")

    /** All tasks registered by the hot reload plugin for the Android target of an application module */
    private fun androidTasks(): List<Task> {
        val project = androidProject(AndroidProjectKind.Kmp)
        project.evaluate()
        return project.tasks.filter { it.isAndroidHotTask() }
    }
}
