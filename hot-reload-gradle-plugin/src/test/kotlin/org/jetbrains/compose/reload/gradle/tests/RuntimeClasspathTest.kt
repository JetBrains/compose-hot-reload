/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.gradle.tests

import org.gradle.testfixtures.ProjectBuilder
import org.jetbrains.compose.reload.core.HOT_RELOAD_VERSION
import org.jetbrains.compose.reload.core.testFixtures.PathRegex
import org.jetbrains.compose.reload.core.testFixtures.assertMatches
import org.jetbrains.compose.reload.gradle.ComposeHotReloadPlugin
import org.jetbrains.compose.reload.gradle.composeHotReloadAgentClasspath
import org.jetbrains.compose.reload.gradle.composeHotReloadAgentJar
import org.jetbrains.compose.reload.gradle.composeHotReloadRuntimeClasspath
import org.jetbrains.compose.reload.gradle.utils.withRepositories
import kotlin.test.Test

class RuntimeClasspathTest {
    @Test
    fun `test - resolve agent classpath`() {
        val project = ProjectBuilder.builder().build()
        project.withRepositories()
        project.composeHotReloadAgentClasspath.files.assertMatches(
            PathRegex(""".*hot-reload-agent-$HOT_RELOAD_VERSION.jar"""), { true },
        )
    }

    @Test
    fun `test - resolve agent jar`() {
        val project = ProjectBuilder.builder().build()
        project.withRepositories()
        project.composeHotReloadAgentJar.files.assertMatches(
            PathRegex(""".*hot-reload-agent-$HOT_RELOAD_VERSION.jar""")
        )
    }

    @Test
    fun `test - hot reload runtime classpath`() {
        val project = ProjectBuilder.builder().build()
        project.withRepositories()
        project.plugins.apply(ComposeHotReloadPlugin::class.java)

        project.composeHotReloadRuntimeClasspath.files.assertMatches(
            PathRegex(".*hot-reload-runtime-jvm-$HOT_RELOAD_VERSION.jar"),
            PathRegex(".*hot-reload-annotations-jvm-$HOT_RELOAD_VERSION.jar"),
            { true }
        )
    }
}
