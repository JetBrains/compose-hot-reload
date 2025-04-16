/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.tests

import kotlinx.serialization.json.JsonPrimitive
import org.gradle.api.Project
import org.gradle.kotlin.dsl.support.serviceOf
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry
import org.jetbrains.compose.reload.ComposeHotReloadPlugin
import org.jetbrains.compose.reload.core.HOT_RELOAD_VERSION
import org.jetbrains.compose.reload.core.asFileName
import org.jetbrains.compose.reload.core.testFixtures.sanitized
import org.jetbrains.compose.reload.gradle.idea.IdeaComposeHotReloadModel
import org.jetbrains.compose.reload.gradle.kotlinMultiplatformOrNull
import org.jetbrains.compose.reload.test.core.InternalHotReloadTestApi
import org.jetbrains.compose.reload.test.core.TestEnvironment
import org.jetbrains.compose.reload.utils.evaluate
import org.jetbrains.compose.reload.utils.withRepositories
import org.jetbrains.kotlin.gradle.plugin.KotlinMultiplatformPluginWrapper
import org.jetbrains.kotlin.gradle.plugin.KotlinPluginWrapper
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import kotlin.io.path.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.notExists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.fail

class ModelBuilderTest {
    @Test
    fun `test - kmp project - 1`(testInfo: TestInfo) {
        val project = ProjectBuilder.builder().build()
        project.withRepositories()

        project.plugins.apply(KotlinMultiplatformPluginWrapper::class.java)
        project.plugins.apply(ComposeHotReloadPlugin::class.java)
        project.kotlinMultiplatformOrNull?.jvm()

        checkModel(project, testInfo)
    }

    @Test
    fun `test - jvm project - 1`(testInfo: TestInfo) {
        val project = ProjectBuilder.builder().build()
        project.withRepositories()
        project.plugins.apply(KotlinPluginWrapper::class.java)
        project.plugins.apply(ComposeHotReloadPlugin::class.java)

        checkModel(project, testInfo)
    }

    @OptIn(InternalHotReloadTestApi::class)
    private fun checkModel(project: Project, testInfo: TestInfo) {
        project.evaluate()

        val model = project.serviceOf<ToolingModelBuilderRegistry>()
            .getBuilder(IdeaComposeHotReloadModel::class.java.name)
            .buildAll(IdeaComposeHotReloadModel::class.java.name, project)
            ?: fail("Missing model for ${testInfo.displayName}")

        val actualText = model.toString().testSanitized(project)

        val expectFileName = testInfo.testMethod.get().name.asFileName().plus(".json")
        val expectFile = Path("src/test/resources/idea-model/$expectFileName")

        if (TestEnvironment.updateTestData) {
            expectFile.createParentDirectories().writeText(actualText)
            return
        }

        if (expectFile.notExists()) {
            expectFile.createParentDirectories().writeText(actualText)
            fail("Expected model file ${expectFile.toUri()} did not exist; Generated")
        }

        val expectText = expectFile.readText().testSanitized(project)
        if (expectText != actualText) {
            val actualFile = expectFile.resolveSibling("${expectFile.nameWithoutExtension}-actual.json")
            actualFile.writeText(actualText)
            fail("Idea Model '${expectFile.toUri()}' did not match")
        }
    }

    private fun String.testSanitized(project: Project): String {
        return replace(
            JsonPrimitive(project.projectDir.path).toString().removePrefix("\"").removeSuffix("\""),
            "<PROJECT_DIR>"
        ).replace(HOT_RELOAD_VERSION, "<HOT_RELOAD_VERSION>")
            .replace("""\\""", "/")
            .sanitized()
    }
}
