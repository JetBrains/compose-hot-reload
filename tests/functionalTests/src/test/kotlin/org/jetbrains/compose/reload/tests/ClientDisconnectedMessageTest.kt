package org.jetbrains.compose.reload.tests

import org.jetbrains.compose.reload.orchestration.OrchestrationClientRole
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ClientDisconnected
import org.jetbrains.compose.reload.utils.DefaultBuildGradleKts
import org.jetbrains.compose.reload.utils.DefaultSettingsGradleKts
import org.jetbrains.compose.reload.utils.HotReloadTest
import org.jetbrains.compose.reload.utils.HotReloadTestFixture
import org.jetbrains.compose.reload.utils.TestOnlyKmp
import org.jetbrains.compose.reload.utils.TestOnlyLatestVersions
import org.jetbrains.compose.reload.utils.launchApplication
import org.jetbrains.compose.reload.utils.writeText
import kotlin.time.Duration.Companion.minutes

class ClientDisconnectedMessageTest {

    @HotReloadTest
    @TestOnlyLatestVersions
    @TestOnlyKmp
    @DefaultSettingsGradleKts
    @DefaultBuildGradleKts
    fun `send shutdown request - receive ClientDisconnected`(
        testFixture: HotReloadTestFixture
    ) = testFixture.runTest(timeout = 5.minutes) {
        testFixture.projectDir.writeText(
            "src/jvmMain/kotlin/Main.kt", """
            import androidx.compose.material3.*
            import org.jetbrains.compose.reload.test.*
            import androidx.compose.ui.unit.sp
            fun main() {
                screenshotTestApplication {
                    TestText("Hello", fontSize = 48.sp)
                }
            }
        """.trimIndent()
        )

        testFixture.runTransaction {
            testFixture.launchApplication()
            skipToMessage<OrchestrationMessage.UIRendered>()
        }

        testFixture.sendMessage(OrchestrationMessage.ShutdownRequest()) {
            skipToMessage<ClientDisconnected> { message ->
                message.clientRole == OrchestrationClientRole.Application
            }
        }
    }
}
