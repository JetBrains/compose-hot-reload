/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.tests

import org.jetbrains.compose.reload.test.gradle.HotReloadTest
import org.jetbrains.compose.reload.test.gradle.HotReloadTestFixture
import org.jetbrains.compose.reload.test.gradle.build
import org.jetbrains.compose.reload.test.gradle.initialSourceCode
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import kotlin.time.Duration.Companion.minutes

class Warmup {
    /**
     * Warmup test which shall ensure that 'actual' tests have all dependencies downloaded.
     */
    @HotReloadTest
    @Execution(ExecutionMode.SAME_THREAD)
    fun build(fixture: HotReloadTestFixture) = fixture.runTest(timeout = 15.minutes) {
        fixture initialSourceCode """
            import androidx.compose.foundation.layout.*
            import androidx.compose.ui.unit.sp
            import androidx.compose.ui.window.*
            import org.jetbrains.compose.reload.test.*
            
            fun main() {
                screenshotTestApplication {
                    TestText("Hello")
                }
            }
            """.trimIndent()
    }
}
