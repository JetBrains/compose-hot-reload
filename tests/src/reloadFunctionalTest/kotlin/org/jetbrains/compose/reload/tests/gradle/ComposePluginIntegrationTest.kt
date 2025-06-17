/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.tests.gradle

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import org.jetbrains.compose.reload.orchestration.OrchestrationClientRole
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ClientDisconnected
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.TestEvent
import org.jetbrains.compose.reload.test.gradle.ApplicationLaunchMode
import org.jetbrains.compose.reload.test.gradle.HotReloadTest
import org.jetbrains.compose.reload.test.gradle.HotReloadTestFixture
import org.jetbrains.compose.reload.test.gradle.ProjectMode
import org.jetbrains.compose.reload.test.gradle.TestedLaunchMode
import org.jetbrains.compose.reload.test.gradle.TestedProjectMode
import org.jetbrains.compose.reload.test.gradle.assertSuccessful
import org.jetbrains.compose.reload.test.gradle.buildFlow
import org.jetbrains.compose.reload.utils.GradleIntegrationTest
import org.jetbrains.compose.reload.utils.TestOnlyDefaultKotlinVersion
import kotlin.io.path.appendText
import kotlin.io.path.createParentDirectories
import kotlin.io.path.writeText

class ComposePluginIntegrationTest {

    @GradleIntegrationTest
    @HotReloadTest
    @TestedLaunchMode(ApplicationLaunchMode.Detached)
    @TestedProjectMode(ProjectMode.Kmp)
    @TestOnlyDefaultKotlinVersion
    fun `test - mainClass convention`(fixture: HotReloadTestFixture) = fixture.runTest {
        fixture.projectDir.buildGradleKts.appendText(
            """
            |compose.desktop.application {
            |    mainClass = "my.pkg.MainKt"
            |}
            """.trimMargin()
        )

        fixture.projectDir.resolve("src/commonMain/kotlin/Main.kt")
            .createParentDirectories()
            .writeText(
                """
                |package my.pkg
                |import org.jetbrains.compose.reload.test.*
                |
                |fun main() {
                |   sendTestEventBlocking("Running: Main")
                |}
                """.trimMargin()
            )

        fixture.projectDir.resolve("src/commonMain/kotlin/Foo.kt")
            .createParentDirectories()
            .writeText(
                """
                |package my.foo
                |import org.jetbrains.compose.reload.test.*
                |
                |fun main() {
                |   sendTestEventBlocking("Running: Foo")
                |}
                """.trimMargin()
            )

        /* Check: We use the mainClass from compose.desktop.application as default */
        fixture.runTransaction {
            launch {
                fixture.gradleRunner.buildFlow("jvmRunHot").toList().assertSuccessful()
            }
            skipToMessage<TestEvent>("Waiting for application 'alive' signal") { it.payload == "Running: Main" }
            skipToMessage<ClientDisconnected> { it.clientRole == OrchestrationClientRole.Application }
        }


        /* Check: We can provide the mainClass using a Gradle property */
        fixture.runTransaction {
            launch {
                fixture.gradleRunner.buildFlow("jvmRunHot", "-PmainClass=my.foo.FooKt").toList().assertSuccessful()
            }
            skipToMessage<TestEvent>("Waiting for application 'alive' signal") { it.payload == "Running: Foo" }
            skipToMessage<ClientDisconnected> { it.clientRole == OrchestrationClientRole.Application }
        }

        /* Check: We can provide the mainClass using a System property */
        fixture.runTransaction {
            launch {
                fixture.gradleRunner.buildFlow("jvmRunHot", "-DmainClass=my.foo.FooKt").toList().assertSuccessful()
            }
            skipToMessage<TestEvent>("Waiting for application 'alive' signal") { it.payload == "Running: Foo" }
            skipToMessage<ClientDisconnected> { it.clientRole == OrchestrationClientRole.Application }
        }

        /* Check: We can provide the mainClass the CLI option*/
        fixture.runTransaction {
            launch {
                fixture.gradleRunner.buildFlow("jvmRunHot", "--mainClass=my.foo.FooKt").toList().assertSuccessful()
            }
            skipToMessage<TestEvent>("Waiting for application 'alive' signal") { it.payload == "Running: Foo" }
            skipToMessage<ClientDisconnected> { it.clientRole == OrchestrationClientRole.Application }
        }
    }
}
