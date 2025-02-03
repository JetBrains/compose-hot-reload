package org.jetbrains.compose.reload.tests.gradle

import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.RecompileRequest
import org.jetbrains.compose.reload.utils.DefaultBuildGradleKts
import org.jetbrains.compose.reload.utils.DefaultSettingsGradleKts
import org.jetbrains.compose.reload.utils.HotReloadTest
import org.jetbrains.compose.reload.utils.HotReloadTestFixture
import org.jetbrains.compose.reload.utils.TestOnlyDefaultCompilerOptions
import org.jetbrains.compose.reload.utils.TestOnlyLatestVersions
import org.jetbrains.compose.reload.utils.checkScreenshot
import org.jetbrains.compose.reload.utils.replaceSourceCode
import kotlin.io.path.appendLines
import kotlin.io.path.createFile
import kotlin.io.path.createParentDirectories
import kotlin.test.assertEquals

class NonContinuousRecompileTest {
    @HotReloadTest
    @TestOnlyLatestVersions
    @TestOnlyDefaultCompilerOptions
    @DefaultSettingsGradleKts
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
