package org.jetbrains.compose.reload.tests.gradle

import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.RecompileRequest
import org.jetbrains.compose.reload.test.gradle.DefaultBuildGradleKts
import org.jetbrains.compose.reload.test.gradle.HotReloadTest
import org.jetbrains.compose.reload.test.gradle.HotReloadTestFixture
import org.jetbrains.compose.reload.test.gradle.TestOnlyDefaultCompilerOptions
import org.jetbrains.compose.reload.test.gradle.TestOnlyLatestVersions
import org.jetbrains.compose.reload.test.gradle.checkScreenshot
import org.jetbrains.compose.reload.test.gradle.replaceSourceCode
import kotlin.io.path.appendLines
import kotlin.io.path.createFile
import kotlin.io.path.createParentDirectories
import kotlin.test.assertEquals

class NonContinuousRecompileTest {
    @HotReloadTest
    @TestOnlyLatestVersions
    @TestOnlyDefaultCompilerOptions
    @DefaultBuildGradleKts
    fun `test - non continuous build`(fixture: HotReloadTestFixture) = fixture.runTest {
        projectDir.resolve("gradle.properties")
            .createParentDirectories().createFile()
            .appendLines(listOf("compose.build.continuous=false"))

        runTransaction {
            this initialSourceCode """
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

            skipToMessage<OrchestrationMessage.RecompileResult>()
            fixture.checkScreenshot("0-before")
        }

        replaceSourceCode("Before", "After")
        checkScreenshot("1-beforeRequest")

        runTransaction {
            val recompileRequest = RecompileRequest()

            launchChildTransaction {
                val result = skipToMessage<OrchestrationMessage.RecompileResult> { result ->
                    result.recompileRequestId == recompileRequest.messageId
                }
                assertEquals(0, result.exitCode)
            }

            launchChildTransaction {
                skipToMessage<OrchestrationMessage.UIRendered>()
                fixture.checkScreenshot("2-afterRecompile")
            }

            recompileRequest.send()
        }
    }
}
