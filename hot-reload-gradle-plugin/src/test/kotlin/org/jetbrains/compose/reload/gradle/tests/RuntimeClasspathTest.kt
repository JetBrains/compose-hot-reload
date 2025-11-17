/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.gradle.tests

import org.gradle.kotlin.dsl.dependencies
import org.gradle.testfixtures.ProjectBuilder
import org.jetbrains.compose.reload.core.HOT_RELOAD_VERSION
import org.jetbrains.compose.reload.core.testFixtures.PathRegex
import org.jetbrains.compose.reload.core.testFixtures.assertMatches
import org.jetbrains.compose.reload.core.testFixtures.minus
import org.jetbrains.compose.reload.core.testFixtures.regexEscaped
import org.jetbrains.compose.reload.gradle.ComposeHotReloadPlugin
import org.jetbrains.compose.reload.gradle.composeHotReloadAgentClasspath
import org.jetbrains.compose.reload.gradle.composeHotReloadAgentJar
import org.jetbrains.compose.reload.gradle.composeHotReloadRuntimeClasspath
import org.jetbrains.compose.reload.gradle.kotlinJvmOrNull
import org.jetbrains.compose.reload.gradle.utils.evaluate
import org.jetbrains.compose.reload.gradle.utils.withRepositories
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.Test
import kotlin.test.fail

class RuntimeClasspathTest {
    @Test
    fun `test - resolve agent classpath`() {
        val project = ProjectBuilder.builder().build()
        project.withRepositories()
        project.composeHotReloadAgentClasspath.files.assertMatches(
            PathRegex(""".*hot-reload-agent-${HOT_RELOAD_VERSION.regexEscaped}.jar"""), { true },
        )
    }

    @Test
    fun `test - resolve agent jar`() {
        val project = ProjectBuilder.builder().build()
        project.withRepositories()
        project.composeHotReloadAgentJar.files.assertMatches(
            PathRegex(""".*hot-reload-agent-${HOT_RELOAD_VERSION.regexEscaped}.jar""")
        )
    }

    @Test
    fun `test - hot reload runtime classpath`() {
        val project = ProjectBuilder.builder().build()
        project.withRepositories()
        project.plugins.apply(ComposeHotReloadPlugin::class.java)

        project.composeHotReloadRuntimeClasspath.files.assertMatches(
            PathRegex(".*hot-reload-runtime-jvm-${HOT_RELOAD_VERSION.regexEscaped}.jar"),
            PathRegex(".*hot-reload-annotations-jvm-${HOT_RELOAD_VERSION.regexEscaped}.jar"),
            { true }
        )
    }

    @Test
    fun `test - agent classpath should not resolve kotlin stdlib`() {
        val kotlinStdlib = PathRegex(".*stdlib.*")
        val project = ProjectBuilder.builder().build()
        project.withRepositories()

        val kotlinStdlibFiles = project.composeHotReloadAgentClasspath.files.filter { file ->
            kotlinStdlib.matches(file)
        }

        if (kotlinStdlibFiles.isNotEmpty()) {
            fail("classpath should not resolve them in kotlin stdlib files: $kotlinStdlibFiles")
        }
    }

    @ParameterizedTest(name = "Kotlin Version: {0}")
    @ValueSource(strings = ["2.2.0", "2.2.20"])
    fun `test - user defined kotlin-stdlib`(version: String) {
        val project = ProjectBuilder.builder().build()
        project.withRepositories()
        project.plugins.apply("org.jetbrains.kotlin.jvm")
        project.plugins.apply("org.jetbrains.compose")
        project.plugins.apply("org.jetbrains.kotlin.plugin.compose")
        project.plugins.apply(ComposeHotReloadPlugin::class.java)

        project.dependencies {
            "implementation"("org.jetbrains.kotlin:kotlin-stdlib:$version")
        }

        project.evaluate()

        val main = project.kotlinJvmOrNull!!.target.compilations.getByName("main")
        main.composeHotReloadRuntimeClasspath.assertMatches(
            PathRegex(".*kotlin-stdlib-${Regex.escape(version)}.jar"),
            (PathRegex(".*") - PathRegex(".*stdlib.*")) // everything, except any other kotlin-stdlib
        )
    }
}
