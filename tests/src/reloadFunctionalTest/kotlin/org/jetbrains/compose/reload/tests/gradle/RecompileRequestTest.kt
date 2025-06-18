/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.tests.gradle

import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.info
import org.jetbrains.compose.reload.core.warn
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.RecompileRequest
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.RecompileResult
import org.jetbrains.compose.reload.test.gradle.HotReloadTest
import org.jetbrains.compose.reload.test.gradle.HotReloadTestFixture
import org.jetbrains.compose.reload.test.gradle.checkScreenshot
import org.jetbrains.compose.reload.test.gradle.initialSourceCode
import org.jetbrains.compose.reload.test.gradle.replaceSourceCode
import org.jetbrains.compose.reload.utils.GradleIntegrationTest
import org.jetbrains.compose.reload.utils.QuickTest
import kotlin.test.assertEquals

@QuickTest
class RecompileRequestTest {
    private val logger = createLogger()

    @HotReloadTest
    @GradleIntegrationTest
    fun `test - non continuous build`(fixture: HotReloadTestFixture) = fixture.runTest {
        fixture initialSourceCode """
                import androidx.compose.foundation.layout.*
                import androidx.compose.ui.unit.sp
                import androidx.compose.ui.window.*
                import org.jetbrains.compose.reload.test.*
                
                fun main() {
                    screenshotTestApplication {
                        TestText("Before")
                    }
                }
                """.trimIndent()

        fixture.checkScreenshot("0-before")
        replaceSourceCode("Before", "After")
        checkScreenshot("1-beforeRequest")

        runTransaction {
            val recompileRequest = RecompileRequest()

            launchChildTransaction {
                val result = skipToMessage<RecompileResult>(
                    "Waiting for recompile result of '${recompileRequest.messageId}'"
                ) { result ->
                    if (result.recompileRequestId != recompileRequest.messageId) {
                        logger.warn(
                            "Suspicious RecompileResult: ${result.recompileRequestId}; " +
                                "expected: ${recompileRequest.messageId}"
                        )
                    }

                    result.recompileRequestId == recompileRequest.messageId
                }
                assertEquals(0, result.exitCode)
            }

            launchChildTransaction {
                skipToMessage<OrchestrationMessage.UIRendered>()
                fixture.checkScreenshot("2-afterRecompile")
            }

            recompileRequest.send()
            logger.info("Recompile Request sent: $recompileRequest")
        }
    }
}
