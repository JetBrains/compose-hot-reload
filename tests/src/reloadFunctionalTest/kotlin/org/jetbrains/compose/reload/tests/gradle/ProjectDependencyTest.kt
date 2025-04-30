/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.tests.gradle

import org.jetbrains.compose.reload.core.HOT_RELOAD_VERSION
import org.jetbrains.compose.reload.test.gradle.BuildGradleKts
import org.jetbrains.compose.reload.test.gradle.HotReloadTest
import org.jetbrains.compose.reload.test.gradle.HotReloadTestFixture
import org.jetbrains.compose.reload.test.gradle.ProjectMode
import org.jetbrains.compose.reload.test.gradle.checkScreenshot
import org.jetbrains.compose.reload.test.gradle.fold
import org.jetbrains.compose.reload.test.gradle.launchApplicationAndWait
import org.jetbrains.compose.reload.test.gradle.replaceSourceCodeAndReload
import org.jetbrains.compose.reload.test.gradle.writeText
import org.jetbrains.compose.reload.utils.GradleIntegrationTest
import org.jetbrains.compose.reload.utils.HostIntegrationTest
import kotlin.io.path.appendLines
import kotlin.io.path.appendText
import kotlin.io.path.createParentDirectories
import kotlin.io.path.writeText

class ProjectDependencyTest {
    @HotReloadTest
    @HostIntegrationTest
    @GradleIntegrationTest
    @BuildGradleKts("app")
    fun `test - change in dependency project`(
        fixture: HotReloadTestFixture,
    ) = doTest(fixture, dependencyProjectHasPluginApplied = true)


    @HotReloadTest
    @HostIntegrationTest
    @GradleIntegrationTest
    @BuildGradleKts("app")
    fun `test - change in dependency project - missing plugin in dependency project`(
        fixture: HotReloadTestFixture,
    ) = doTest(fixture, dependencyProjectHasPluginApplied = false)


    fun doTest(fixture: HotReloadTestFixture, dependencyProjectHasPluginApplied: Boolean) = fixture.runTest {
        fixture.projectDir.settingsGradleKts.appendLines(
            listOf(
                "",
                """include(":widgets")""",
                """include(":app")"""
            )
        )

        fixture.projectDir.subproject("app").buildGradleKts.appendText("""
            kotlin {
                jvmToolchain(21)
            }
            
        """.trimIndent())

        if (fixture.projectMode == ProjectMode.Kmp) {
            fixture.projectDir.subproject("app").buildGradleKts.appendText(
                """
                kotlin {
                    sourceSets.commonMain.dependencies {
                        implementation(project(":widgets"))
                    }
                }
                """.trimIndent()
            )
        }

        if (fixture.projectMode == ProjectMode.Jvm) {
            fixture.projectDir.subproject("app").buildGradleKts.appendText(
                """
                dependencies {
                    implementation(project(":widgets"))
                }
            """.trimIndent()
            )
        }

        fixture.projectDir.subproject("widgets").buildGradleKts.createParentDirectories().writeText(
            """
            plugins {
            ${
                when (fixture.projectMode) {
                    ProjectMode.Kmp -> "kotlin(\"multiplatform\")"
                    ProjectMode.Jvm -> "kotlin(\"jvm\")"
                }
            }
                kotlin("plugin.compose")
                id("org.jetbrains.compose")
                ${if (dependencyProjectHasPluginApplied) """id("org.jetbrains.compose.hot-reload")""" else ""}
            }
            
            kotlin {
                jvmToolchain(21)
            }
            
            ${
                when (fixture.projectMode) {
                    ProjectMode.Kmp -> """
                        kotlin {
                            jvm()
                            sourceSets.commonMain.dependencies {
                                implementation(compose.desktop.currentOs)
                                implementation("org.jetbrains.compose.hot-reload:hot-reload-test:$HOT_RELOAD_VERSION")
                            }
                        }
                    """

                    ProjectMode.Jvm -> """
                        dependencies {
                            implementation(compose.desktop.currentOs)
                            implementation("org.jetbrains.compose.hot-reload:hot-reload-test:$HOT_RELOAD_VERSION")
                        }
                    """
                }
            }
        """.trimIndent()
        )

        fixture.projectDir.writeText(
            "widgets/src/${fixture.projectMode.fold("commonMain", "main")}/kotlin/Widget.kt", """
            import androidx.compose.runtime.Composable
            import androidx.compose.ui.unit.sp
            import org.jetbrains.compose.reload.test.*
            
            @Composable
            fun Widget(text: String) {
                TestText("Before: " + text)
            }
            """.trimIndent()
        )

        fixture.projectDir.writeText(
            "app/src/${fixture.projectMode.fold("commonMain", "main")}/kotlin/Main.kt", """
            import androidx.compose.foundation.layout.*
            import androidx.compose.ui.unit.sp
            import androidx.compose.ui.window.*
            import org.jetbrains.compose.reload.test.*
            
            fun main() {
                screenshotTestApplication {
                    Widget("Hello") // <- calls into other module
                }
            }
            """.trimIndent()
        )

        fixture.launchApplicationAndWait(":app")
        fixture.checkScreenshot("before")

        fixture.replaceSourceCodeAndReload(
            "widgets/src/${fixture.projectMode.fold("commonMain", "main")}/kotlin/Widget.kt",
            "Before:", "After:"
        )
        fixture.checkScreenshot("after")
    }
}
