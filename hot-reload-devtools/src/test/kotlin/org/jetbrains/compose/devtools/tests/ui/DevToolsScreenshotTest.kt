/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.devtools.tests.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import org.jetbrains.compose.devtools.sidecar.DtExpandedSidecarWindowContent
import org.jetbrains.compose.devtools.states.ConsoleLogState
import org.jetbrains.compose.devtools.states.ReloadState
import org.jetbrains.compose.devtools.states.ReloadState.Failed
import org.jetbrains.compose.devtools.states.ReloadState.Reloading
import org.jetbrains.compose.devtools.utils.DtScreenshotTest
import org.jetbrains.compose.devtools.utils.DtScreenshotTestFixture
import org.jetbrains.compose.devtools.utils.ScreenshotTest
import org.jetbrains.compose.devtools.utils.checkScreenshot
import org.jetbrains.compose.devtools.utils.runScreenshotTest
import org.jetbrains.compose.devtools.utils.set
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import kotlin.time.Duration.Companion.seconds


@OptIn(ExperimentalTestApi::class)
@Execution(ExecutionMode.SAME_THREAD)
class DevToolsScreenshotTest : ScreenshotTest {

    @DtScreenshotTest
    context(fixture: DtScreenshotTestFixture)
    fun `test - empty state`() = runScreenshotTest {
        setContent {
            DtExpandedSidecarWindowContent()
        }

        waitForIdle()
        checkScreenshot("empty-state")
    }

    @DtScreenshotTest
    context(fixture: DtScreenshotTestFixture)
    fun `test - reload state - failure`() = runScreenshotTest {
        setContent {
            DtExpandedSidecarWindowContent()
        }

        ReloadState set Failed(reason = "Oh-oh", time = systemTime)
        waitForIdle()
        checkScreenshot("failure")


        advanceTimeBy(1.seconds)
        waitForIdle()
        checkScreenshot("failure-1s-ago")
    }

    @DtScreenshotTest
    context(fixture: DtScreenshotTestFixture)
    fun `test - reload state - reloading`() = runScreenshotTest {
        setContent {
            DtExpandedSidecarWindowContent()
        }

        ReloadState set Reloading(systemTime)
        waitForIdle()
        checkScreenshot("reloading")
    }

    @DtScreenshotTest
    context(fixture: DtScreenshotTestFixture)
    fun `test - console`() = runScreenshotTest {
        setContent {
            DtExpandedSidecarWindowContent()
        }

        ConsoleLogState set ConsoleLogState(
            listOf(
                "Harry Potter is best watched through a Coldmirror",
                "No this is Patrick!"
            )
        )

        waitForIdle()
        checkScreenshot("initial")
    }
}
