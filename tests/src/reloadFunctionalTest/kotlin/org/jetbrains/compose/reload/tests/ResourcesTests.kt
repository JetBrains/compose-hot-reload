/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.tests

import org.jetbrains.compose.reload.core.testFixtures.repositoryRoot
import org.jetbrains.compose.reload.test.gradle.BuildGradleKtsExtension
import org.jetbrains.compose.reload.test.gradle.ExtendBuildGradleKts
import org.jetbrains.compose.reload.test.gradle.HotReloadTest
import org.jetbrains.compose.reload.test.gradle.HotReloadTestFixture
import org.jetbrains.compose.reload.test.gradle.ProjectMode
import org.jetbrains.compose.reload.test.gradle.TestedProjectMode
import org.jetbrains.compose.reload.test.gradle.checkScreenshot
import org.jetbrains.compose.reload.test.gradle.getDefaultMainKtSourceFile
import org.jetbrains.compose.reload.test.gradle.initialSourceCode
import org.jetbrains.compose.reload.utils.GradleIntegrationTest
import org.jetbrains.compose.reload.utils.QuickTest
import org.junit.jupiter.api.extension.ExtensionContext
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.io.path.createParentDirectories
import kotlin.io.path.moveTo
import kotlin.io.path.name

@HotReloadTest
@GradleIntegrationTest
@QuickTest
@TestedProjectMode(ProjectMode.Kmp)
@ExtendBuildGradleKts(ResourcesTests.Extension::class)
class ResourcesTests {

    @HotReloadTest
    fun `rename resource`(fixture: HotReloadTestFixture) = fixture.runTest {
        val testResourceDir = fixture.projectDir
            .resolve("src")
            .resolve("commonMain")
            .resolve("composeResources/drawable")

        val originalResourceName = "testDrawableResource"

        repositoryRoot.resolve("")

        val testResource = testResourceDir.resolve("$originalResourceName.xml")
            .createParentDirectories()

        val classLoader = Thread.currentThread().contextClassLoader
        (classLoader.getResourceAsStream("${ResourcesTests::class.java.simpleName}/testVectorResource.xml")
            ?: error("Resource not found")).use { input ->
            Files.copy(input, testResource, StandardCopyOption.REPLACE_EXISTING)
        }

        val projectName = fixture.projectDir.path.name.replace('-', '_')
        fixture initialSourceCode """
            import androidx.compose.foundation.Image
            import org.jetbrains.compose.reload.test.*
            import ${projectName}.generated.resources.*
            import org.jetbrains.compose.resources.painterResource
            
            fun main() {
                screenshotTestApplication {
                    Image(painterResource(Res.drawable.$originalResourceName), null)
                }
            }
            """.trimIndent()
        fixture.checkScreenshot("initial")

        // rename resource
        val renamedResourceName = "testDrawableResourceRenamed"
        fixture.runTransaction {
            testResource.moveTo(testResourceDir.resolve("$renamedResourceName.xml"), overwrite = true)
            replaceSourceCodeAndReload(
                sourceFile = fixture.getDefaultMainKtSourceFile(),
                oldValue = originalResourceName,
                newValue = renamedResourceName
            )
        }
        // nothing should change
        fixture.checkScreenshot("initial")
    }

    class Extension : BuildGradleKtsExtension {
        override fun commonDependencies(context: ExtensionContext): String {
            return "implementation(compose.components.resources)"
        }
    }
}
