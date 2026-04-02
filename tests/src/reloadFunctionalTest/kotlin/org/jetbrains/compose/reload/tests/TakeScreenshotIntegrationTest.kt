/*
 * Copyright 2024-2026 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.tests

import org.jetbrains.compose.devtools.api.WindowsState
import org.jetbrains.compose.reload.test.gradle.CheckScreenshot
import org.jetbrains.compose.reload.test.gradle.Headless
import org.jetbrains.compose.reload.test.gradle.HotReloadTest
import org.jetbrains.compose.reload.test.gradle.HotReloadTestFixture
import org.jetbrains.compose.reload.test.gradle.checkScreenshot
import org.jetbrains.compose.reload.test.gradle.initialSourceCode
import org.jetbrains.compose.reload.utils.HostIntegrationTest
import org.jetbrains.compose.reload.utils.QuickTest
import org.junit.jupiter.api.Assumptions.assumeTrue

class TakeScreenshotIntegrationTest {

    @Headless(false)
    @HostIntegrationTest
    @HotReloadTest
    @CheckScreenshot(radius = 4)
    @QuickTest
    fun `test - take screenshot`(fixture: HotReloadTestFixture) = fixture.runTest {
        assumeTrue(isInteractiveDesktopAvailable(), "Test requires an interactive desktop")
        fixture.launchAckSender()

        val windowsState = fixture.orchestration.states.get(WindowsState)

        fixture initialSourceCode """
            import androidx.compose.foundation.background
            import androidx.compose.foundation.layout.Box
            import androidx.compose.ui.Modifier
            import androidx.compose.ui.graphics.Color
            import androidx.compose.ui.unit.dp
            import androidx.compose.ui.window.*
            import org.jetbrains.compose.reload.test.TestText

            fun main() {
               singleWindowApplication(
                   state = WindowState(width = 300.dp, height = 200.dp),
                   undecorated = true,
               ) {
                   Box(modifier = Modifier.background(Color.White)) {
                       TestText("Hello World!")
                   }
               }
            }
            """.trimIndent()

        awaitOneWindow(windowsState)
        fixture.checkScreenshot("screenshot")
    }
}
