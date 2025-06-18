/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.compose.reload.tests

import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.info
import org.jetbrains.compose.reload.orchestration.OrchestrationClientRole
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import org.jetbrains.compose.reload.test.gradle.BuildGradleKtsExtension
import org.jetbrains.compose.reload.test.gradle.Headless
import org.jetbrains.compose.reload.test.gradle.HotReloadTest
import org.jetbrains.compose.reload.test.gradle.HotReloadTestDimensionExtension
import org.jetbrains.compose.reload.test.gradle.HotReloadTestFixture
import org.jetbrains.compose.reload.test.gradle.HotReloadTestInvocationContext
import org.jetbrains.compose.reload.test.gradle.initialSourceCode
import org.jetbrains.compose.reload.utils.HostIntegrationTest
import org.jetbrains.compose.reload.utils.QuickTest
import org.junit.jupiter.api.extension.ExtensionContext
import kotlin.jvm.optionals.getOrNull
import kotlin.time.Duration.Companion.seconds

class DevtoolsIntegrationTests {
    private val logger = createLogger()

    /**
     * This test will launch an empty application, but in *non-headless* mode:
     * Because the test is started non-headless, we can expect the devtools process to be launched
     *  successfully. The test will assert that a client with [OrchestrationClientRole.Tooling] connects
     * to our [org.jetbrains.compose.reload.orchestration.OrchestrationServer].
     */
    @HotReloadTest
    @QuickTest
    @HostIntegrationTest
    @Headless(false)
    fun `test - devtools - is launched`(fixture: HotReloadTestFixture) = fixture.runTest {
        runTransaction {
            /* Empty application: We're not testing the application, but the launch of the devtools */
            fixture.initialSourceCode(
                """
                import org.jetbrains.compose.reload.test.*
                
                fun main() {
                    screenshotTestApplication {
                    }
                }
                """.trimIndent()
            )

            /* The devtools are expected to be alive very quickly. The 30s timeout is generous. */
            skipToMessage<OrchestrationMessage.ClientConnected>(
                title = "Waiting for devtools to connect",
                timeout = 30.seconds
            ) { message ->
                logger.info("client connected: ${message.clientRole}")
                message.clientRole == OrchestrationClientRole.Tooling
            }
        }
    }
}

/**
 * Generic extension which will only run against tests declared in [DevtoolsIntegrationTests]
 */
internal class DevtoolsIntegrationTestsExtension : BuildGradleKtsExtension, HotReloadTestDimensionExtension {
    override fun javaExecConfigure(context: ExtensionContext): String? {
        if (context.testClass.getOrNull() != DevtoolsIntegrationTests::class.java) return null

        /*
        We're launching the application in 'non-headless' mode, but we still would prevent
        the system from showing an icon in the taskbar.
         */
        return """
            systemProperty("apple.awt.UIElement", true)
        """.trimIndent()
    }

    override fun transform(
        context: ExtensionContext,
        tests: List<HotReloadTestInvocationContext>
    ): List<HotReloadTestInvocationContext> {
        if (context.testClass.getOrNull() != DevtoolsIntegrationTests::class.java) return tests

        /*
        Actually, we do not really care about test-dimensions. Anyone should be fine:
        Let's just pick the last one available in the transformation chain.
         */
        return tests.takeLast(1)
    }
}
