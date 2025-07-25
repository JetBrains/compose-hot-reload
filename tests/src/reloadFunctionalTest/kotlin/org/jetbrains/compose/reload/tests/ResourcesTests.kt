/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.tests

import org.jetbrains.compose.reload.core.HotReloadProperty.ResourcesDirtyResolverEnabled
import org.jetbrains.compose.reload.test.gradle.BuildGradleKtsExtension
import org.jetbrains.compose.reload.test.gradle.DisabledVersion
import org.jetbrains.compose.reload.test.gradle.ExtendBuildGradleKts
import org.jetbrains.compose.reload.test.gradle.HotReloadTest
import org.jetbrains.compose.reload.test.gradle.HotReloadTestFixture
import org.jetbrains.compose.reload.test.gradle.MinComposeVersion
import org.jetbrains.compose.reload.test.gradle.ProjectMode
import org.jetbrains.compose.reload.test.gradle.TestedProjectMode
import org.jetbrains.compose.reload.test.gradle.WithHotReloadProperty
import org.jetbrains.compose.reload.test.gradle.checkScreenshot
import org.jetbrains.compose.reload.test.gradle.getDefaultMainKtSourceFile
import org.jetbrains.compose.reload.test.gradle.initialSourceCode
import org.jetbrains.compose.reload.utils.GradleIntegrationTest
import org.jetbrains.compose.reload.utils.QuickTest
import org.junit.jupiter.api.extension.ExtensionContext
import java.nio.file.Files
import java.nio.file.Files.writeString
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.createParentDirectories
import kotlin.io.path.moveTo
import kotlin.io.path.name

@HotReloadTest
@GradleIntegrationTest
@TestedProjectMode(ProjectMode.Kmp)
@ExtendBuildGradleKts(ResourcesTests.Extension::class)
@WithHotReloadProperty(ResourcesDirtyResolverEnabled, "true")
class ResourcesTests {

    private fun HotReloadTestFixture.projectName() = projectDir.path.name.replace('-', '_')

    private suspend fun HotReloadTestFixture.drawableResourceUsageSource(resourceName: String): Path {
        return initialSourceCode("""
            import androidx.compose.foundation.Image
            import org.jetbrains.compose.reload.test.*
            import ${projectName()}.generated.resources.*
            import org.jetbrains.compose.resources.painterResource
            
            fun main() {
                screenshotTestApplication {
                    Image(painterResource(Res.drawable.$resourceName), null)
                }
            }
            """.trimIndent())
    }

    private fun HotReloadTestFixture.testResourceDir(): Path {
        return projectDir
            .resolve("src")
            .resolve("commonMain")
            .resolve("composeResources")
    }

    private fun HotReloadTestFixture.testDrawableResource(resourceName: String): Path {
        return testResourceDir()
            .resolve("drawable")
            .resolve(resourceName)
            .createParentDirectories()
            .also {
                copyTestResource("testVectorResource.xml", it)
            }
    }

    private fun HotReloadTestFixture.testFontResource(resourceName: String): Path {
        return testResourceDir()
            .resolve("font")
            .resolve(resourceName)
            .createParentDirectories()
    }

    private fun copyTestResource(resource: String, to: Path) {
        val classLoader = Thread.currentThread().contextClassLoader
        (classLoader.getResourceAsStream("${ResourcesTests::class.java.simpleName}/$resource")
            ?: error("Resource not found")).use { input ->
            Files.copy(input, to, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    @QuickTest
    @HotReloadTest
    fun `rename resource`(fixture: HotReloadTestFixture) = fixture.runTest {
        val originalResourceName = "testDrawableResource"
        val testResource = testDrawableResource("$originalResourceName.xml")

        fixture.drawableResourceUsageSource(originalResourceName)

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

    @QuickTest
    @HotReloadTest
    fun `replace drawable resource`(fixture: HotReloadTestFixture) = fixture.runTest {
        val resourceName = "testDrawableResource"

        val testResource = testDrawableResource("$resourceName.xml")

        fixture.drawableResourceUsageSource(resourceName)

        fixture.checkScreenshot("initial")

        fixture.runTransaction {
            copyTestResource("testVectorResource2.xml", testResource)
            requestReload()
        }
        fixture.checkScreenshot("replaced")
    }

    private fun HotReloadTestFixture.testStringResourceFile(): Path {
        return testResourceDir()
            .resolve("values")
            .resolve("strings.xml")
            .createParentDirectories()
    }

    private fun HotReloadTestFixture.testStringResourceChange(
        resourceName: String,
        writeResource: (String, String) -> Unit,
        importStatement: String,
        resourceAccess: String
    ) = runTest {
        writeResource(resourceName, "Before")

        initialSourceCode(
            """
        import org.jetbrains.compose.reload.test.*
        import ${projectName()}.generated.resources.*
        import $importStatement
        
        fun main() {
            screenshotTestApplication {
                TestText($resourceAccess)
            }
        }
        """.trimIndent()
        )
        checkScreenshot("before")

        runTransaction {
            writeResource(resourceName, "After")
            requestReload()
        }
        checkScreenshot("after")
    }

    private fun stringsXmlResource(resourceSection: String): String {
        return """
        <?xml version="1.0" encoding="utf-8"?>
        <resources>
            $resourceSection
        </resources>
        """.trimIndent()
    }

    @QuickTest
    @HotReloadTest
    fun `change string resource`(fixture: HotReloadTestFixture) {
        val stringResource = fixture.testStringResourceFile()
        val resourceName = "testStringResource"
        fixture.testStringResourceChange(
            resourceName = resourceName,
            writeResource = { name, value ->
                writeString(
                    stringResource,
                    stringsXmlResource("<string name=\"$name\">$value</string>")
                )
            },
            importStatement = "org.jetbrains.compose.resources.stringResource",
            resourceAccess = "stringResource(Res.string.$resourceName)"
        )
    }

    @QuickTest
    @HotReloadTest
    fun `change plural string resource`(fixture: HotReloadTestFixture) {
        val stringResource = fixture.testStringResourceFile()
        val resourceName = "testPluralStringResource"
        fixture.testStringResourceChange(
            resourceName = resourceName,
            writeResource = { name, value ->
                writeString(
                    stringResource,
                    stringsXmlResource(
                        """<plurals name="$name"><item quantity="one">%1${'$'}d $value</item></plurals>"""
                    )
                )
            },
            importStatement = "org.jetbrains.compose.resources.pluralStringResource",
            resourceAccess = "pluralStringResource(Res.plurals.$resourceName, 1, 1)"
        )
    }

    @QuickTest
    @HotReloadTest
    fun `change array string resource`(fixture: HotReloadTestFixture) {
        val stringResource = fixture.testStringResourceFile()
        val resourceName = "testArrayStringResource"
        fixture.testStringResourceChange(
            resourceName = resourceName,
            writeResource = { name, value ->
                writeString(
                    stringResource,
                    stringsXmlResource(
                        """<string-array name="$name"><item>$value</item></string-array>"""
                    )
                )
            },
            importStatement = "org.jetbrains.compose.resources.stringArrayResource",
            resourceAccess = "stringArrayResource(Res.array.$resourceName)[0]"
        )
    }

    @HotReloadTest
    // Unfortunately, we can not order alpha and dev compose versions, so we have to manually disable alpha versions
    // where there was no support for font resources.
    // TODO: set @MinComposeVersion to 1.9.0-alpha04 when it is available.
    @DisabledVersion(compose = "1.9.0-alpha02", reason = "No support for font resources cache invalidation")
    @MinComposeVersion("1.9.0+dev2620")
    fun `replace font resource`(fixture: HotReloadTestFixture) = fixture.runTest {
        val resourceName = "testFontResource"

        val testResource = testFontResource("$resourceName.ttf")
        copyTestResource("testFontResource.ttf", testResource)

        fixture.initialSourceCode(
            """
                import androidx.compose.ui.text.font.FontFamily
                import androidx.compose.material3.Text
                import androidx.compose.ui.unit.sp
                import org.jetbrains.compose.reload.test.*
                import ${fixture.projectName()}.generated.resources.*
                import org.jetbrains.compose.resources.Font
                
                fun main() {
                    screenshotTestApplication {
                        Text("Font resource",
                            fontSize = 48.sp,
                            fontFamily = FontFamily(Font(Res.font.$resourceName)),
                        )
                    }
                }
                """.trimIndent()
        )

        fixture.checkScreenshot("initial")

        fixture.runTransaction {
            copyTestResource("testFontResource2.ttf", testResource)
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
