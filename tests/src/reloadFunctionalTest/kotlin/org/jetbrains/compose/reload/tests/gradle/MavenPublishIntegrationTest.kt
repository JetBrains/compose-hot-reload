/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.tests.gradle

import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.reload.test.gradle.GradleRunner
import org.jetbrains.compose.reload.test.gradle.TestedGradleVersion
import org.jetbrains.compose.reload.test.gradle.assertSuccess
import org.jetbrains.compose.reload.test.gradle.build
import org.jetbrains.compose.reload.test.gradle.renderSettingsGradleKts
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolver
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.minutes

class MavenPublishIntegrationTest {
    companion object {
        val timeout = 5.minutes
    }

    @Test
    fun `test - publish with compose hot reload plugin applied - jvm`(
        @TempDir dir: Path, @DefaultSettingsGradleKts settingsGradleKts: String
    ) = runTest(timeout = timeout) {
        dir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                kotlin("jvm")
                kotlin("plugin.compose")
                id("org.jetbrains.compose")
                id("org.jetbrains.compose.hot-reload")
                `maven-publish`
            }
            
            group = "org.tested"
            
            publishing {
                publications.create("maven", MavenPublication::class) {
                    from(components["java"])
                }
            }
            
            publishing {
                repositories {
                    maven(file("build/repo")) {
                        name = "local"
                    }
                }
            }
            
            dependencies {
                implementation(compose.desktop.currentOs)
            }
            
            """.trimIndent()
        )

        dir.resolve("settings.gradle.kts").writeText(settingsGradleKts)
        dir.resolve("src/main/kotlin/Main.kt").createParentDirectories().writeText("fun main() {}")

        GradleRunner(
            projectRoot = dir,
            gradleVersion = TestedGradleVersion.default.version
        ).build(":publishAllPublicationsToLocalRepository").assertSuccess()
    }

    @Test
    fun `test - publish with compose hot reload plugin applied - kmp`(
        @TempDir dir: Path, @DefaultSettingsGradleKts settingsGradleKts: String
    ) = runTest(timeout = timeout) {
        dir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                kotlin("multiplatform")
                kotlin("plugin.compose")
                id("org.jetbrains.compose")
                id("org.jetbrains.compose.hot-reload")
                `maven-publish`
            }
            
            group = "org.tested"
            
            publishing {
                repositories {
                    maven(file("build/repo")) {
                        name = "local"
                    }
                }
            }
            
            kotlin {
                jvm()
                sourceSets.commonMain.dependencies {
                    implementation(compose.desktop.currentOs)
                }
            }
            """.trimIndent()
        )

        dir.resolve("settings.gradle.kts").writeText(settingsGradleKts)
        dir.resolve("src/main/kotlin/Main.kt").createParentDirectories().writeText("fun main() {}")

        GradleRunner(
            projectRoot = dir,
            gradleVersion = TestedGradleVersion.default.version,
        ).build(":publishAllPublicationsToLocalRepository").assertSuccess()
    }
}

@ExtendWith(DefaultSettingsGradleKtsExtension::class)
private annotation class DefaultSettingsGradleKts

private class DefaultSettingsGradleKtsExtension : ParameterResolver {
    override fun supportsParameter(
        parameterContext: ParameterContext,
        extensionContext: ExtensionContext
    ): Boolean {
        return parameterContext.parameter.type == String::class.java &&
            parameterContext.parameter.isAnnotationPresent(DefaultSettingsGradleKts::class.java)
    }

    override fun resolveParameter(
        parameterContext: ParameterContext,
        extensionContext: ExtensionContext
    ): Any {
        return renderSettingsGradleKts(extensionContext)
    }
}
