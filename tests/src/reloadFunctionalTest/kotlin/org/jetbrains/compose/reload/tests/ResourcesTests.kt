/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.tests

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
import java.nio.file.Path
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

    private suspend fun HotReloadTestFixture.resourceUsageSource(resourceName: String): Path {
        val projectName = projectDir.path.name.replace('-', '_')
        return initialSourceCode("""
            import androidx.compose.foundation.Image
            import org.jetbrains.compose.reload.test.*
            import ${projectName}.generated.resources.*
            import org.jetbrains.compose.resources.painterResource
            
            fun main() {
                screenshotTestApplication {
                    Image(painterResource(Res.drawable.$resourceName), null)
                }
            }
            """.trimIndent())
    }

    private fun HotReloadTestFixture.testResource(resourceName: String): Path {
        return projectDir
            .resolve("src")
            .resolve("commonMain")
            .resolve("composeResources/drawable")
            .resolve(resourceName)
            .createParentDirectories()
            .also {
                copyTestResource("testVectorResource.xml", it)
            }
    }

    private fun copyTestResource(resource: String, to: Path) {
        val classLoader = Thread.currentThread().contextClassLoader
        (classLoader.getResourceAsStream("${ResourcesTests::class.java.simpleName}/$resource")
            ?: error("Resource not found")).use { input ->
            Files.copy(input, to, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    @HotReloadTest
    fun `rename resource`(fixture: HotReloadTestFixture) = fixture.runTest {
        val originalResourceName = "testDrawableResource"
        val testResource = testResource("$originalResourceName.xml")

        fixture.resourceUsageSource(originalResourceName)

        fixture.checkScreenshot("initial")

        // rename resource
        val renamedResourceName = "testDrawableResourceRenamed"
        fixture.runTransaction {
            testResource.moveTo(testResource.parent.resolve("$renamedResourceName.xml"), overwrite = true)
            replaceSourceCodeAndReload(
                sourceFile = fixture.getDefaultMainKtSourceFile(),
                oldValue = originalResourceName,
                newValue = renamedResourceName
            )
        }
        // nothing should change
        fixture.checkScreenshot("initial")
    }

    @HotReloadTest
    fun `replace drawable resource`(fixture: HotReloadTestFixture) = fixture.runTest {
        val resourceName = "testDrawableResource"

        val testResource = testResource("$resourceName.xml")

        fixture.resourceUsageSource(resourceName)
        fixture.checkScreenshot("initial")

        fixture.runTransaction {
            copyTestResource("testVectorResource2.xml", testResource)
            requestReload()
        }
        fixture.checkScreenshot("replaced")
    }

    class Extension : BuildGradleKtsExtension {
        override fun commonDependencies(context: ExtensionContext): String {
            return "implementation(compose.components.resources)"
        }
    }
}
