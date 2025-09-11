/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.tests

import org.jetbrains.compose.devtools.api.ReloadState
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import org.jetbrains.compose.reload.test.gradle.HotReloadTest
import org.jetbrains.compose.reload.test.gradle.HotReloadTestFixture
import org.jetbrains.compose.reload.test.gradle.ReloadOverlay
import org.jetbrains.compose.reload.test.gradle.TransactionScope
import org.jetbrains.compose.reload.test.gradle.checkScreenshot
import org.jetbrains.compose.reload.test.gradle.initialSourceCode
import kotlin.test.assertIs
import kotlin.time.ExperimentalTime

class ReloadEffectsTest {
    @OptIn(ExperimentalTime::class)
    @ReloadOverlay(
        overlayEnabled = true,
        animationsEnabled = false,
    )
    @HotReloadTest
    fun `test - reload effects`(fixture: HotReloadTestFixture) = fixture.runTest {
        val reloadState = orchestration.states.get(ReloadState)
        fixture initialSourceCode """
            import androidx.compose.foundation.layout.*
            import androidx.compose.ui.unit.sp
            import androidx.compose.ui.window.*
            import org.jetbrains.compose.reload.test.*
            
            fun main() {
                screenshotTestApplication {}
            }
            """.trimIndent()

        assertIs<ReloadState.Ok>(reloadState.value)
        fixture.checkScreenshot("before")

        runTransaction {
            updateReloadStateAndAwait(fixture, ReloadState.Reloading())
            fixture.checkScreenshot("reloading")
        }

        runTransaction {
            updateReloadStateAndAwait(fixture, ReloadState.Failed(reason = "Test"))
            fixture.checkScreenshot("failed")
        }

        runTransaction {
            updateReloadStateAndAwait(fixture, ReloadState.Ok())
            fixture.checkScreenshot("ok")
        }
    }

    private suspend fun TransactionScope.updateReloadStateAndAwait(
        fixture: HotReloadTestFixture,
        newState: ReloadState,
    ) {
        fixture.orchestration.update(ReloadState) { newState }
        skipToMessage<OrchestrationMessage.LogMessage> { message ->
            message.message.contains("Recomposing ReloadEffects")
        }
    }
}