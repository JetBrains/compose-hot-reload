/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.tests.gradle

import kotlinx.coroutines.delay
import org.jetbrains.compose.reload.core.HOT_RELOAD_VERSION
import org.jetbrains.compose.reload.core.asTemplateOrThrow
import org.jetbrains.compose.reload.core.launchTask
import org.jetbrains.compose.reload.core.renderOrThrow
import org.jetbrains.compose.reload.core.testFixtures.PathRegex
import org.jetbrains.compose.reload.core.testFixtures.assertMatches
import org.jetbrains.compose.reload.core.testFixtures.assertNotMatches
import org.jetbrains.compose.reload.core.testFixtures.regexEscaped
import org.jetbrains.compose.reload.core.testFixtures.repositoryRoot
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.TestEvent
import org.jetbrains.compose.reload.test.core.AppClasspath
import org.jetbrains.compose.reload.test.core.CompilerOption
import org.jetbrains.compose.reload.test.gradle.ApplicationLaunchMode
import org.jetbrains.compose.reload.test.gradle.BuildGradleKts
import org.jetbrains.compose.reload.test.gradle.BuildGradleKtsExtension
import org.jetbrains.compose.reload.test.gradle.HotReloadTest
import org.jetbrains.compose.reload.test.gradle.HotReloadTestFixture
import org.jetbrains.compose.reload.test.gradle.OverrideBuildGradleKts
import org.jetbrains.compose.reload.test.gradle.ProjectMode
import org.jetbrains.compose.reload.test.gradle.TestedLaunchMode
import org.jetbrains.compose.reload.test.gradle.TestedProjectMode
import org.jetbrains.compose.reload.test.gradle.compilerOptions
import org.jetbrains.compose.reload.test.gradle.getDefaultMainKtSourceFile
import org.jetbrains.compose.reload.test.gradle.launchApplication
import org.jetbrains.compose.reload.test.gradle.projectMode
import org.jetbrains.compose.reload.test.gradle.testedAndroidVersion
import org.jetbrains.compose.reload.utils.GradleIntegrationTest
import org.jetbrains.compose.reload.utils.QuickTest
import org.junit.jupiter.api.extension.ExtensionContext
import java.io.File
import java.lang.System.lineSeparator
import kotlin.io.path.appendText
import kotlin.io.path.createParentDirectories
import kotlin.io.path.exists
import kotlin.io.path.pathString
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.milliseconds

@GradleIntegrationTest
@TestedLaunchMode(ApplicationLaunchMode.Detached)
@QuickTest
class RuntimeDependenciesTest {

    @HotReloadTest
    @TestedProjectMode(ProjectMode.Kmp)
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
            PathRegex(".*/app/build/run/jvmMain/classpath/hot"),
            PathRegex(".*/app/build/run/jvmMain/classpath/libs/lib/.*/lib-jvm.jar"),
            *stdlib,
            *hotReloadAgentDependencies,
            *hotReloadRuntimeDependencies,
            *testRuntimeDependencies,
            *remoteDependencies
        )
    }

    @HotReloadTest
    @TestedProjectMode(ProjectMode.Kmp)
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
            PathRegex(".*/app/build/run/jvmMain/classpath/hot"),
            PathRegex(".*/app/build/run/jvmMain/classpath/libs/lib/.*/lib-jvm.jar"),
            *stdlib,
            *hotReloadAgentDependencies,
            *hotReloadRuntimeDependencies,
            *testRuntimeDependencies,
            *remoteDependencies
        )
    }

    @HotReloadTest
    @TestedProjectMode(ProjectMode.Jvm)
    fun `test - hot jvm project`(fixture: HotReloadTestFixture) = fixture.runTest {
        fixture.resolveRuntimeClasspath().assertMatches(
            PathRegex(".*/build/run/main/classpath/classes"),
            PathRegex(".*/build/run/main/classpath/hot"),
            *stdlib,
            *hotReloadAgentDependencies,
            *hotReloadRuntimeDependencies,
            *testRuntimeDependencies,
            *remoteDependencies
        )
    }

    @HotReloadTest
    @TestedProjectMode(ProjectMode.Kmp)
    fun `test - dev run`(fixture: HotReloadTestFixture) = fixture.runTest {
        val devKt = projectDir.resolve("src/jvmDev/kotlin/Dev.kt")
        devKt.createParentDirectories().writeText(
            """
            import org.jetbrains.compose.reload.test.*
            import androidx.compose.runtime.Composable
            import org.jetbrains.compose.reload.DevelopmentEntryPoint
            
            @Composable
            @DevelopmentEntryPoint
            fun Test() {
                val classpath = System.getProperty("java.class.path")
                sendTestEvent(classpath)
            }
        """.trimIndent()
        )

        val classpath = runTransaction {
            launchChildTransaction { launchDevApplicationAndWait(className = "DevKt", funName = "Test") }
            (skipToMessage<TestEvent>().payload as String).split(File.pathSeparator).map(::File)
        }

        classpath.assertMatches(
            PathRegex(".*/build/run/jvmDev/classpath/classes"),
            PathRegex(".*/build/run/jvmDev/classpath/hot"),
            PathRegex(".*/build/run/jvmDev/classpath/libs/opaque/.*/main.jar"), // Dependency on main compilatin output

            /* Dev compilations automatically see the runtime api */
            PathRegex("${repositoryRoot.pathString}/build/repo/.*/hot-reload-runtime-api-jvm-${HOT_RELOAD_VERSION.regexEscaped}.jar"),
            *stdlib,
            *hotReloadAgentDependencies,
            *hotReloadRuntimeDependencies,
            *testRuntimeDependencies,
            *remoteDependencies
        )
    }

    @HotReloadTest
    @TestedProjectMode(ProjectMode.Jvm)
    @OverrideBuildGradleKts(ImplicitRuntimeDependenciesExtension::class)
    fun `test - jvm implicit dependencies`(fixture: HotReloadTestFixture) = fixture.runTest {
        fixture.resolveImplicitRuntimeClasspath()
            .assertMatches(
                PathRegex(".*/build/run/main/classpath/classes"),
                PathRegex(".*/build/run/main/classpath/hot"),
                *stdlib,
                *hotReloadAgentDependencies,
                *hotReloadRuntimeDependencies,
                *remoteDependencies
            )
            .assertNotMatches(
                *testRuntimeDependencies,
                *materialDependencies,
            )
    }

    @HotReloadTest
    @TestedProjectMode(ProjectMode.Kmp)
    @OverrideBuildGradleKts(ImplicitRuntimeDependenciesExtension::class)
    fun `test - kmp implicit dependencies`(fixture: HotReloadTestFixture) = fixture.runTest {
        fixture.resolveImplicitRuntimeClasspath()
            .assertMatches(
                PathRegex(".*/build/run/jvmMain/classpath/classes"),
                PathRegex(".*/build/run/jvmMain/classpath/hot"),
                *stdlib,
                *hotReloadAgentDependencies,
                *hotReloadRuntimeDependencies,
                *remoteDependencies
            )
            .assertNotMatches(
                *testRuntimeDependencies,
                *materialDependencies,
            )
    }
}

private suspend fun HotReloadTestFixture.resolveRuntimeClasspath(projectPath: String = ""): List<File> {
    return runTransaction {
        this@resolveRuntimeClasspath.projectDir.subproject(projectPath)
            .resolve(this@resolveRuntimeClasspath.getDefaultMainKtSourceFile())
            .createParentDirectories().writeText(
                """
                    import org.jetbrains.compose.reload.test.*
                    
                    fun main() = screenshotTestApplication {
                    }
                    """.trimIndent()
            )
        try {
            this@resolveRuntimeClasspath.launchApplication(":$projectPath")
            (skipToMessage<TestEvent>().payload as AppClasspath).files.map(::File)
        } finally {
            OrchestrationMessage.ShutdownRequest("Explicitly requested by the test").send()
        }
    }
}


private suspend fun HotReloadTestFixture.resolveImplicitRuntimeClasspath(
    projectPath: String = ""
): List<File> {
    return runTransaction {
        val classpathFile = this@resolveImplicitRuntimeClasspath.projectDir.resolve("classpath.txt")
        this@resolveImplicitRuntimeClasspath.projectDir.subproject(projectPath)
            .resolve(this@resolveImplicitRuntimeClasspath.getDefaultMainKtSourceFile())
            .createParentDirectories().writeText(
                """
            import kotlin.io.path.Path
            import kotlin.io.path.bufferedWriter
            import kotlin.io.path.writeText

            fun main() {
                val classpath = System.getProperty("java.class.path")
                System.err.println(Path("classpath.txt").toAbsolutePath().toString())
                Path("classpath.txt").bufferedWriter().use { it.write(classpath) }
            }
""".trimIndent()
            )
        try {
            this@resolveImplicitRuntimeClasspath.launchApplication(":$projectPath")
            launchTask {
                // await for the application to finish
                while (!classpathFile.exists()) {
                    delay(100.milliseconds)
                }
            }.await()
            classpathFile.readText().split(File.pathSeparatorChar).map(::File)
        } finally {
            OrchestrationMessage.ShutdownRequest("Explicitly requested by the test").send()
        }
    }
}


class ImplicitRuntimeDependenciesExtension : BuildGradleKtsExtension {
    override fun plugins(context: ExtensionContext): String = """
        kotlin("{{kotlin.plugin}}")
        kotlin("plugin.compose")
        id("org.jetbrains.compose")
        id("org.jetbrains.compose.hot-reload")
        {{if ${"android.enabled"}}}
        id("com.android.application")
        {{/if}}
    """.trimIndent().asTemplateOrThrow().renderOrThrow {
        "kotlin.plugin"(if (context.projectMode == ProjectMode.Jvm) "jvm" else "multiplatform")
        "android.enabled"(context.testedAndroidVersion != null)
        buildTemplate(context)
    }

    override fun jvmMainDependencies(context: ExtensionContext): String = """
            implementation(compose.desktop.currentOs) {
                exclude(group = "org.jetbrains.compose.material")
                exclude(group = "org.jetbrains.compose.material3")
            }
    """.trimIndent()


    override fun composeCompiler(context: ExtensionContext): String? {
        val options = context.compilerOptions
        return listOfNotNull(
            "featureFlags.add(ComposeFeatureFlag.OptimizeNonSkippingGroups)".takeIf {
                options.getValue(CompilerOption.OptimizeNonSkippingGroups)
            },
            "featureFlags.add(ComposeFeatureFlag.OptimizeNonSkippingGroups.disabled())".takeUnless {
                options.getValue(CompilerOption.OptimizeNonSkippingGroups)
            },
        ).joinToString(lineSeparator()).ifEmpty { return null }
    }

    override fun compilerOptions(context: ExtensionContext): String {
        return """freeCompilerArgs.add("-Xindy-allow-annotated-lambdas=${context.compilerOptions.getValue(CompilerOption.IndyAllowAnnotatedLambdas)}")"""
    }
}

private val stdlib = arrayOf(
    PathRegex(".*annotations-.*.jar"),
    PathRegex(".*kotlin-stdlib.*.jar"),
)

private val hotReloadAgentDependencies = arrayOf(
    PathRegex(".*/modules-2/.*/asm-9.9.jar"),
    PathRegex(".*/modules-2/.*/asm-tree-9.9.jar"),
    PathRegex(".*/modules-2/.*/javassist-3.30.2-GA.jar"),
    PathRegex("${repositoryRoot.pathString}/build/repo/.*/hot-reload-agent-${HOT_RELOAD_VERSION.regexEscaped}.jar"),
    PathRegex("${repositoryRoot.pathString}/build/repo/.*/hot-reload-core-${HOT_RELOAD_VERSION.regexEscaped}.jar"),
    PathRegex("${repositoryRoot.pathString}/build/repo/.*/hot-reload-orchestration-${HOT_RELOAD_VERSION.regexEscaped}.jar"),
    PathRegex("${repositoryRoot.pathString}/build/repo/.*/hot-reload-analysis-${HOT_RELOAD_VERSION.regexEscaped}.jar"),
)

private val hotReloadRuntimeDependencies = arrayOf(
    PathRegex("${repositoryRoot.pathString}/build/repo/.*/hot-reload-annotations-jvm-${HOT_RELOAD_VERSION.regexEscaped}.jar"),
    PathRegex("${repositoryRoot.pathString}/build/repo/.*/hot-reload-runtime-jvm-${HOT_RELOAD_VERSION.regexEscaped}.jar"),
    PathRegex("${repositoryRoot.pathString}/build/repo/.*/hot-reload-devtools-api-${HOT_RELOAD_VERSION.regexEscaped}.jar"),
)

private val testRuntimeDependencies = arrayOf(
    PathRegex("${repositoryRoot.pathString}/build/repo/.*/hot-reload-test-${HOT_RELOAD_VERSION.regexEscaped}.jar"),
    PathRegex("${repositoryRoot.pathString}/build/repo/.*/hot-reload-test-core-${HOT_RELOAD_VERSION.regexEscaped}.jar"),
)

private val remoteDependencies = arrayOf(
    PathRegex(".*/modules-2/files-2.1/.*")
)

private val materialDependencies = arrayOf(
    PathRegex(".*/modules-2/files-2.1/.*org\\.jetbrains\\.compose\\.material3\\.*/"),
    PathRegex(".*/modules-2/files-2.1/.*org\\.jetbrains\\.compose\\.material\\.*/"),
)
