package org.jetbrains.compose.reload.tests.gradle

import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.reload.test.gradle.GradleRunner
import org.jetbrains.compose.reload.test.gradle.TestedGradleVersion
import org.jetbrains.compose.reload.test.gradle.assertSuccess
import org.jetbrains.compose.reload.test.gradle.build
import org.jetbrains.compose.reload.test.gradle.createDefaultSettingsGradleKtsContent
import org.junit.jupiter.api.Test
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
    fun `test - publish with compose hot reload plugin applied - jvm`(@TempDir dir: Path) = runTest(timeout = timeout) {
        dir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                kotlin("jvm")
                kotlin("plugin.compose")
                id("org.jetbrains.compose")
                id("org.jetbrains.compose-hot-reload")
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

        dir.resolve("settings.gradle.kts").writeText(createDefaultSettingsGradleKtsContent())
        dir.resolve("src/main/kotlin/Main.kt").createParentDirectories().writeText("fun main() {}")

        GradleRunner(
            projectRoot = dir,
            gradleVersion = TestedGradleVersion.entries.last().version
        ).build(":publishAllPublicationsToLocalRepository").assertSuccess()
    }

    @Test
    fun `test - publish with compose hot reload plugin applied - kmp`(@TempDir dir: Path) = runTest(timeout = timeout) {
        dir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                kotlin("multiplatform")
                kotlin("plugin.compose")
                id("org.jetbrains.compose")
                id("org.jetbrains.compose-hot-reload")
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

        dir.resolve("settings.gradle.kts").writeText(createDefaultSettingsGradleKtsContent())
        dir.resolve("src/main/kotlin/Main.kt").createParentDirectories().writeText("fun main() {}")

        GradleRunner(
            projectRoot = dir,
            gradleVersion = TestedGradleVersion.entries.last().version
        ).build(":publishAllPublicationsToLocalRepository").assertSuccess()
    }
}
