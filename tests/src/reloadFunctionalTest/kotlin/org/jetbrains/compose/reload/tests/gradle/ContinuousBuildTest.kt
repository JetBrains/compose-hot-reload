/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

@file:Suppress("DuplicatedCode")

package org.jetbrains.compose.reload.tests.gradle

import kotlinx.coroutines.flow.toList
import org.jetbrains.compose.reload.test.gradle.BuildMode
import org.jetbrains.compose.reload.test.gradle.HotReloadTest
import org.jetbrains.compose.reload.test.gradle.HotReloadTestFixture
import org.jetbrains.compose.reload.test.gradle.ProjectMode
import org.jetbrains.compose.reload.test.gradle.TestedBuildMode
import org.jetbrains.compose.reload.test.gradle.TestedProjectMode
import org.jetbrains.compose.reload.test.gradle.assertSuccessful
import org.jetbrains.compose.reload.test.gradle.buildFlow
import org.jetbrains.compose.reload.test.gradle.checkScreenshot
import org.jetbrains.compose.reload.test.gradle.getDefaultMainKtSourceFile
import org.jetbrains.compose.reload.test.gradle.initialSourceCode
import org.jetbrains.compose.reload.test.gradle.replaceSourceCode
import org.jetbrains.compose.reload.utils.GradleIntegrationTest
import org.jetbrains.compose.reload.utils.QuickTest
import kotlin.io.path.createParentDirectories
import kotlin.io.path.writeText

@GradleIntegrationTest
@QuickTest
class ContinuousBuildTest {

    @TestedBuildMode(BuildMode.Continuous)
    @HotReloadTest
    fun `test - simple change`(fixture: HotReloadTestFixture) = fixture.runTest {
        fixture initialSourceCode """
            import org.jetbrains.compose.reload.test.*
            
            fun main() {
                screenshotTestApplication {
                    TestText("0")
                }
            }
            """.trimIndent()

        checkScreenshot("0")

        runTransaction {
            fixture.replaceSourceCode("0", "1")
            awaitReload()

            fixture.checkScreenshot("1")
        }

        runTransaction {
            fixture.replaceSourceCode("1", "2")
            awaitReload()

            fixture.checkScreenshot("2")
        }
    }

    @HotReloadTest
    @TestedProjectMode(ProjectMode.Kmp)
    fun `test - launch with cli option`(fixture: HotReloadTestFixture) = fixture.runTest {
        val main = projectDir.resolve(getDefaultMainKtSourceFile())
        main.createParentDirectories().writeText(
            """
            import org.jetbrains.compose.reload.test.*
            
            fun main() {
                screenshotTestApplication {
                    TestText("0")
                }
            }
            """.trimIndent()
        )

        gradleRunner.buildFlow(":hotRunJvmAsync", "--mainClass", "MainKt", "--auto").toList()
            .assertSuccessful()

        runTransaction {
            fixture.replaceSourceCode("0", "1")
            awaitReload()

            fixture.checkScreenshot("1")
        }

        runTransaction {
            fixture.replaceSourceCode("1", "2")
            awaitReload()

            fixture.checkScreenshot("2")
        }
    }
}
