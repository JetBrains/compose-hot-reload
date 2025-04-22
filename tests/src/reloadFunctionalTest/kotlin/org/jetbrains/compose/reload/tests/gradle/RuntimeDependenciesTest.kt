/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.tests.gradle

import org.jetbrains.compose.reload.core.HOT_RELOAD_VERSION
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.TestEvent
import org.jetbrains.compose.reload.test.gradle.ApplicationLaunchMode
import org.jetbrains.compose.reload.test.gradle.BuildGradleKts
import org.jetbrains.compose.reload.test.gradle.HotReloadTest
import org.jetbrains.compose.reload.test.gradle.HotReloadTestFixture
import org.jetbrains.compose.reload.test.gradle.ProjectMode
import org.jetbrains.compose.reload.test.gradle.TestedLaunchMode
import org.jetbrains.compose.reload.test.gradle.TestedProjectMode
import org.jetbrains.compose.reload.test.gradle.getDefaultMainKtSourceFile
import org.jetbrains.compose.reload.test.gradle.launchApplication
import org.jetbrains.compose.reload.utils.GradleIntegrationTest
import org.jetbrains.compose.reload.utils.PathRegex
import org.jetbrains.compose.reload.utils.QuickTest
import org.jetbrains.compose.reload.utils.assertMatches
import java.io.File
import kotlin.io.path.appendText
import kotlin.io.path.createParentDirectories
import kotlin.io.path.writeText

class RuntimeDependenciesTest {

    @HotReloadTest
    @GradleIntegrationTest
    @QuickTest
    @TestedProjectMode(ProjectMode.Kmp)
    @TestedLaunchMode(ApplicationLaunchMode.Detached)
    @BuildGradleKts("app")
    @BuildGradleKts("lib")
    fun `test - hot KMP depending on hot KMP project`(fixture: HotReloadTestFixture) = fixture.runTest {
        fixture.projectDir.subproject("app").buildGradleKts.appendText(
            """
                |
                | kotlin {
                |     sourceSets.commonMain.dependencies {
                |         implementation(project(":lib"))
                |     }
                | }
            """.trimMargin()
        )

        fixture.resolveRuntimeClasspath("app").assertMatches(
            PathRegex(".*/app/build/run/jvmMain/classpath/classes"),
            PathRegex(".*/app/build/run/jvmMain/classpath/libs/hot/lib-jvm.jar"),
            *stdlib,
            *hotReloadAgentDependencies,
            *coldDependencies,
        )
    }

    @HotReloadTest
    @GradleIntegrationTest
    @QuickTest
    @TestedProjectMode(ProjectMode.Kmp)
    @TestedLaunchMode(ApplicationLaunchMode.Detached)
    @BuildGradleKts("app")
    fun `test - hot KMP depending on KMP project wo CHR plugin`(fixture: HotReloadTestFixture) = fixture.runTest {
        fixture.projectDir.settingsGradleKts.appendText(
            """
            include(":lib")
            """.trimIndent()
        )

        fixture.projectDir.subproject("app").buildGradleKts.appendText(
            """
                |
                | kotlin {
                |     sourceSets.commonMain.dependencies {
                |         implementation(project(":lib"))
                |     }
                | }
            """.trimMargin()
        )

        fixture.projectDir.subproject("lib").buildGradleKts.createParentDirectories().writeText(
            """
                plugins {
                    kotlin("multiplatform")
                }
                
                kotlin {
                    jvm()
                    jvmToolchain(21)
                }
            """.trimIndent()
        )

        fixture.resolveRuntimeClasspath("app").assertMatches(
            PathRegex(".*/app/build/run/jvmMain/classpath/classes"),
            PathRegex(".*/app/build/run/jvmMain/classpath/libs/hot/lib-jvm.jar"),
            *stdlib,
            *hotReloadAgentDependencies,
            *coldDependencies,
        )
    }

    @HotReloadTest
    @GradleIntegrationTest
    @QuickTest
    @TestedProjectMode(ProjectMode.Jvm)
    @TestedLaunchMode(ApplicationLaunchMode.Detached)
    fun `test - hot jvm project`(fixture: HotReloadTestFixture) = fixture.runTest {
        fixture.resolveRuntimeClasspath().assertMatches(
            PathRegex(".*/build/run/main/classpath/classes"),
            *stdlib,
            *hotReloadAgentDependencies,
            *coldDependencies,
        )
    }
}

private suspend fun HotReloadTestFixture.resolveRuntimeClasspath(projectPath: String = ""): List<File> =
    runTransaction {
        this@resolveRuntimeClasspath.projectDir.subproject(projectPath)
            .resolve(this@resolveRuntimeClasspath.getDefaultMainKtSourceFile())
            .createParentDirectories().writeText(
                """
                    import org.jetbrains.compose.reload.test.*
                    
                    fun main() = screenshotTestApplication {
                        val classpath = System.getProperty("java.class.path")
                        sendTestEvent(classpath)
                    }
                    """.trimIndent()
            )
        this@resolveRuntimeClasspath.launchApplication(":$projectPath")
        (skipToMessage<TestEvent>().payload as String).split(File.pathSeparator).map(::File)
    }


private val stdlib = arrayOf(
    PathRegex(".*annotations-13.0.jar"),
    PathRegex(".*kotlin-stdlib.*.jar"),
)

private val hotReloadAgentDependencies = arrayOf(
    PathRegex(".*/classpath/agent/asm-9.7.1.jar"),
    PathRegex(".*/classpath/agent/asm-tree-9.7.1.jar"),
    PathRegex(".*/classpath/agent/javassist-3.30.2-GA.jar"),
    PathRegex(".*/classpath/agent/slf4j-api-2.0.16.jar"),
    PathRegex(".*/classpath/agent/agent-$HOT_RELOAD_VERSION.jar"),
    PathRegex(".*/classpath/agent/core-$HOT_RELOAD_VERSION.jar"),
    PathRegex(".*/classpath/agent/orchestration-$HOT_RELOAD_VERSION.jar"),
    PathRegex(".*/classpath/agent/analysis-$HOT_RELOAD_VERSION.jar"),
)

private val coldDependencies = arrayOf(
    PathRegex(".*/classpath/libs/cold/.*")
)
