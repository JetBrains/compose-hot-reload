/*
 * Copyright 2024-2026 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.tests.gradle

import kotlinx.coroutines.flow.toList
import org.jetbrains.compose.reload.test.gradle.AndroidHotReloadTest
import org.jetbrains.compose.reload.test.gradle.BuildGradleKtsExtension
import org.jetbrains.compose.reload.test.gradle.ExtendBuildGradleKts
import org.jetbrains.compose.reload.test.gradle.GradleBuildEvent
import org.jetbrains.compose.reload.test.gradle.HotReloadTest
import org.jetbrains.compose.reload.test.gradle.HotReloadTestFixture
import org.jetbrains.compose.reload.test.gradle.ProjectMode
import org.jetbrains.compose.reload.test.gradle.TestedProjectMode
import org.jetbrains.compose.reload.test.gradle.assertSuccessful
import org.jetbrains.compose.reload.test.gradle.buildFlow
import org.jetbrains.compose.reload.utils.GradleIntegrationTest
import org.jetbrains.compose.reload.utils.QuickTest
import org.jetbrains.compose.reload.utils.TestOnlyAndroid
import org.junit.jupiter.api.extension.ExtensionContext
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * AGP 9+ ships *built-in Kotlin*: the 'kotlin' extension is created by AGP itself and no Kotlin
 * plugin id (neither 'org.jetbrains.kotlin.android' nor 'kotlin("multiplatform")') is applied.
 * We test here a special logic around it: [org.jetbrains.compose.reload.gradle.withBuiltInKotlinAndroidPlugin]
 */
@GradleIntegrationTest
@AndroidHotReloadTest
@TestOnlyAndroid
@TestedProjectMode(ProjectMode.Android)
@QuickTest
@ExtendBuildGradleKts(BuiltInKotlinAndroidTest.DiagnosticsTask::class)
class BuiltInKotlinAndroidTest {

    @HotReloadTest
    fun `test - built-in Kotlin - hot reload tasks are registered`(fixture: HotReloadTestFixture) = fixture.runTest {
        val events = fixture.gradleRunner.buildFlow("chrDiagnostics", "tasks", "--all").toList()
            .assertSuccessful()
        val output = events.output()

        /* Guard: this project really relies on AGP's built-in Kotlin */
        assertTrue("kotlin.android plugin applied: false" in output, "Expected no 'kotlin.android' plugin\n$output")
        assertTrue("kotlin extension: KotlinAndroidProjectExtension" in output, "Expected a Kotlin Android extension\n$output")

        assertEquals(
            setOf("hotReload", "hotRun", "hotSnapshot"),
            output.lineSequence().mapNotNull { line ->
                hotTaskRegex.matchEntire(line.trim())?.groupValues?.get(1)
            }.toSet(),
            "Expected the hot reload tasks of the hot variant\n$output",
        )
    }

    /* Stdout and stderr alike: Gradle routes 'logger.error' to stderr */
    private fun Iterable<GradleBuildEvent>.output(): String =
        filterIsInstance<GradleBuildEvent.Output>().joinToString("\n") { it.line }

    /** Prints the mode-of-Kotlin diagnostics the assertions above guard on. */
    class DiagnosticsTask : BuildGradleKtsExtension {
        override fun footer(context: ExtensionContext): String = """
            tasks.register("chrDiagnostics") {
                val kotlinExtension = project.extensions.findByName("kotlin")?.javaClass?.simpleName
                val legacyPlugin = project.pluginManager.hasPlugin("org.jetbrains.kotlin.android")
                doLast {
                    println("kotlin extension: ${'$'}kotlinExtension")
                    println("kotlin.android plugin applied: ${'$'}legacyPlugin")
                }
            }
        """.trimIndent()
    }

    private companion object {
        /* A line of 'gradle tasks --all': 'hotRun - Runs the Android...' */
        val hotTaskRegex = Regex("""(hot(?:Run|Reload|Snapshot)) - .*""")
    }
}
