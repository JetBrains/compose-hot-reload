/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.tests

import org.jetbrains.compose.devtools.api.ReloadState
import org.jetbrains.compose.devtools.api.VirtualTimeState
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import org.jetbrains.compose.reload.test.gradle.CheckScreenshot
import org.jetbrains.compose.reload.test.gradle.HotReloadTest
import org.jetbrains.compose.reload.test.gradle.HotReloadTestFixture
import org.jetbrains.compose.reload.test.gradle.ReloadEffects
import org.jetbrains.compose.reload.test.gradle.checkScreenshot
import org.jetbrains.compose.reload.test.gradle.initialSourceCode
import org.jetbrains.compose.reload.utils.TestOnlyDefaultCompilerOptions
import org.jetbrains.compose.reload.utils.TestOnlyDefaultKotlinVersion
import kotlin.test.assertIs
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

@TestOnlyDefaultCompilerOptions
@TestOnlyDefaultKotlinVersion

/**
 * Those screenshots are less stable (different rendering on different platforms)
 * We therefore allow a larger tolerance when comparing the screenshots and increase the pixel search radius
 */
@CheckScreenshot(colorTolerance = .25f, radius = 9)
class ReloadEffectsTest {

    @ReloadEffects
    @HotReloadTest
    fun `test - reload effects`(fixture: HotReloadTestFixture) =
        testReloadEffects(
            fixture, """
            import androidx.compose.foundation.layout.*
            import androidx.compose.ui.unit.sp
            import androidx.compose.ui.window.*
            import org.jetbrains.compose.reload.test.*
            
            fun main() {
                screenshotTestApplication {
                    TestText("Servus!")
                }
            }
            """.trimIndent()
        )

    @ReloadEffects
    @HotReloadTest
    fun `test - reload effects on window with unspecified size`(fixture: HotReloadTestFixture) =
        testReloadEffects(
            fixture, """
            import androidx.compose.foundation.layout.*
            import androidx.compose.ui.unit.sp
            import androidx.compose.ui.window.*
            import org.jetbrains.compose.reload.test.*
            
            fun main() {
                screenshotTestApplication(width = -1, height = -1) {
                    TestText("Servus!")
                }
            }
            """.trimIndent()
        )

    @ReloadEffects
    @HotReloadTest
    fun `test - reload effects on window with unspecified width`(fixture: HotReloadTestFixture) =
        testReloadEffects(
            fixture, """
            import androidx.compose.foundation.layout.*
            import androidx.compose.ui.unit.sp
            import androidx.compose.ui.window.*
            import org.jetbrains.compose.reload.test.*
            
            fun main() {
                screenshotTestApplication(width = -1, height = 512) {
                    TestText("Servus!")
                }
            }
            """.trimIndent()
        )

    @ReloadEffects
    @HotReloadTest
    fun `test - reload effects on window with unspecified height`(fixture: HotReloadTestFixture) =
        testReloadEffects(
            fixture, """
            import androidx.compose.foundation.layout.*
            import androidx.compose.ui.unit.sp
            import androidx.compose.ui.window.*
            import org.jetbrains.compose.reload.test.*
            
            fun main() {
                screenshotTestApplication(width = 512, height = -1) {
                    TestText("Servus!")
                }
            }
            """.trimIndent()
        )

    @OptIn(ExperimentalTime::class)
    private fun testReloadEffects(fixture: HotReloadTestFixture, code: String) = fixture.runTest {
        /* Set the virtual time to render animations consistently for this test */
        setTime(0.seconds)

        val reloadState = orchestration.states.get(ReloadState)
        fixture initialSourceCode code

        assertIs<ReloadState.Ok>(reloadState.value)
        advanceTimeBy(1.seconds)
        fixture.checkScreenshot("0-before")

        updateReloadStateAndAwait(ReloadState.Reloading())
        fixture.checkScreenshot("1-reloading-0ms")
        advanceTimeBy(500.milliseconds)
        fixture.checkScreenshot("2-reloading-500ms")

        updateReloadStateAndAwait(ReloadState.Failed(reason = "Test"))
        fixture.checkScreenshot("3-failed-0ms")
        advanceTimeBy(1500.milliseconds)
        fixture.checkScreenshot("4-failed-1500ms")

        updateReloadStateAndAwait(ReloadState.Reloading())
        updateReloadStateAndAwait(ReloadState.Ok())
        fixture.checkScreenshot("5-ok-0ms")
        advanceTimeBy(500.milliseconds)
        fixture.checkScreenshot("6-ok-500ms")
        advanceTimeBy(500.milliseconds)
        fixture.checkScreenshot("7-ok-1000ms")
    }


    private suspend fun HotReloadTestFixture.updateReloadStateAndAwait(newState: ReloadState) {
        runTransaction {
            this@updateReloadStateAndAwait.orchestration.update(ReloadState) { newState }
            skipToMessage<OrchestrationMessage.LogMessage>("Waiting for effects to be rendered") { message ->
                message.message.contains("Recomposing ReloadEffects")
            }
        }
    }

    suspend fun HotReloadTestFixture.setTime(duration: Duration) {
        orchestration.update(VirtualTimeState) { VirtualTimeState(duration) }
    }

    suspend fun HotReloadTestFixture.advanceTimeBy(duration: Duration) {
        orchestration.update(VirtualTimeState) { state ->
            VirtualTimeState((state?.time ?: Duration.ZERO) + duration)
        }
    }
}
