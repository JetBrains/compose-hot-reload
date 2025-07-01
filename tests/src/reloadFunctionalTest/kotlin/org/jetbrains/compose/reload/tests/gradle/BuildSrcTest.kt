/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.tests.gradle

import kotlinx.coroutines.flow.toList
import org.jetbrains.compose.reload.core.HOT_RELOAD_VERSION
import org.jetbrains.compose.reload.test.gradle.ApplicationLaunchMode
import org.jetbrains.compose.reload.test.gradle.BuildGradleKtsExtension
import org.jetbrains.compose.reload.test.gradle.DisabledVersion
import org.jetbrains.compose.reload.test.gradle.ExtendBuildGradleKts
import org.jetbrains.compose.reload.test.gradle.ExtendProjectSetup
import org.jetbrains.compose.reload.test.gradle.HotReloadTest
import org.jetbrains.compose.reload.test.gradle.HotReloadTestFixture
import org.jetbrains.compose.reload.test.gradle.ProjectMode
import org.jetbrains.compose.reload.test.gradle.ProjectSetupExtension
import org.jetbrains.compose.reload.test.gradle.TestedLaunchMode
import org.jetbrains.compose.reload.test.gradle.TestedProjectMode
import org.jetbrains.compose.reload.test.gradle.assertSuccessful
import org.jetbrains.compose.reload.test.gradle.buildFlow
import org.jetbrains.compose.reload.test.gradle.checkScreenshot
import org.jetbrains.compose.reload.test.gradle.testedComposeVersion
import org.jetbrains.compose.reload.test.gradle.testedKotlinVersion
import org.jetbrains.compose.reload.utils.GradleIntegrationTest
import org.jetbrains.compose.reload.utils.LocalMavenRepositoryExtension
import org.jetbrains.compose.reload.utils.TestOnlyDefaultComposeVersion
import org.jetbrains.compose.reload.utils.TestOnlyDefaultKotlinVersion
import org.junit.jupiter.api.extension.ExtensionContext
import kotlin.io.path.createParentDirectories
import kotlin.io.path.writeText

class BuildSrcTest {
    @HotReloadTest
    @GradleIntegrationTest
    @DisabledVersion(gradle = "8.7", reason = "buildSrc compliance requires Gradle 8.11")
    @TestOnlyDefaultComposeVersion
    @TestOnlyDefaultKotlinVersion
    @TestedProjectMode(ProjectMode.Kmp)
    @TestedLaunchMode(ApplicationLaunchMode.Detached)
    @ExtendBuildGradleKts(Extension::class)
    @ExtendProjectSetup(Extension::class)
    fun `test - buildSrc`(fixture: HotReloadTestFixture) = fixture.runTest {
        fixture.gradleRunner.buildFlow("tasks").toList().assertSuccessful()

        /**
         * Test code by actually launching our 'myHotRun' task and taking a screenshot!
         */
        fixture.projectDir.resolve("src/commonMain/kotlin/Main.kt").createParentDirectories().writeText(
            """
            import org.jetbrains.compose.reload.test.*
                
            fun main() = screenshotTestApplication {
                TestText("Foo")
            }
        """.trimIndent()
        )

        fixture.runTransaction {
            fixture.launchTestDaemon {
                fixture.gradleRunner.buildFlow("myHotRun", "--mainClass", "MainKt").toList().assertSuccessful()
            }

            awaitApplicationStart()
        }

        fixture.checkScreenshot("startup")
    }

    class Extension : BuildGradleKtsExtension, ProjectSetupExtension {
        override fun plugins(context: ExtensionContext): String? {
            return """id("my-plugin")"""
        }

        override fun setupProject(fixture: HotReloadTestFixture, context: ExtensionContext) {
            fixture.projectDir.resolve("buildSrc/build.gradle.kts").createParentDirectories().writeText(
                """
                plugins {
                    `kotlin-dsl`    
                }
                
                repositories {
                    ${LocalMavenRepositoryExtension().localMaven()}
                    mavenCentral()
                }
                
                dependencies {
                    implementation("org.jetbrains.compose.hot-reload:hot-reload-gradle-plugin:$HOT_RELOAD_VERSION")
                    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:${context.testedKotlinVersion}")
                    implementation("org.jetbrains.compose:compose-gradle-plugin:${context.testedComposeVersion}")
                }
                """.trimIndent()
            )

            fixture.projectDir.resolve("buildSrc/src/main/kotlin/my-plugin.gradle.kts").createParentDirectories()
                .writeText(
                    """
                    import org.jetbrains.compose.reload.gradle.ComposeHotRun
                    
                    plugins {
                        id("org.jetbrains.compose.hot-reload")
                    }
                    
                    tasks.register("myHotRun", ComposeHotRun::class)
                    """.trimIndent()
                )
        }
    }
}
