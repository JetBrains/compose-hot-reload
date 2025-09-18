/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

@file:OptIn(ExperimentalPathApi::class)

package org.jetbrains.compose.reload.tests

import kotlinx.coroutines.flow.toList
import org.jetbrains.compose.devtools.api.VirtualTimeState
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ShutdownRequest
import org.jetbrains.compose.reload.test.gradle.ExtendProjectSetup
import org.jetbrains.compose.reload.test.gradle.HotReloadTest
import org.jetbrains.compose.reload.test.gradle.HotReloadTestFixture
import org.jetbrains.compose.reload.test.gradle.ProjectSetupExtension
import org.jetbrains.compose.reload.test.gradle.assertSuccessful
import org.jetbrains.compose.reload.test.gradle.buildFlow
import org.jetbrains.compose.reload.test.gradle.checkScreenshot
import org.jetbrains.compose.reload.test.gradle.replaceText
import org.jetbrains.compose.reload.test.gradle.requestAndAwaitReload
import org.jetbrains.compose.reload.utils.QuickTest
import org.jetbrains.compose.reload.utils.TestOnlyDefaultCompilerOptions
import org.jetbrains.compose.reload.utils.TestOnlyDefaultComposeVersion
import org.jetbrains.compose.reload.utils.TestOnlyDefaultKotlinVersion
import org.junit.jupiter.api.extension.ExtensionContext
import java.nio.file.StandardCopyOption
import kotlin.io.path.CopyActionResult
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.copyTo
import kotlin.io.path.copyToRecursively
import kotlin.io.path.createDirectory
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Tests the scenario where the UI (and therefore the runtime binaries) are not loaded at system level!
 */
@ExtendProjectSetup(UIClassLoaderTest.Extension::class)
class UIClassLoaderTest {

    @QuickTest
    @HotReloadTest
    @TestOnlyDefaultKotlinVersion
    @TestOnlyDefaultCompilerOptions
    @TestOnlyDefaultComposeVersion
    fun `test - simple change`(fixture: HotReloadTestFixture) = fixture.runTest {
        orchestration.update(VirtualTimeState) { VirtualTimeState(Duration.ZERO) }

        /* Launch custom application and await the first UI rendering */
        runTransaction {
            fixture.launchTestDaemon {
                fixture.gradleRunner.buildFlow("customHotRun").toList().assertSuccessful()
            }

            skipToMessage<OrchestrationMessage.UIRendered>()
        }

        checkScreenshot("0-before")
        val uiKt = projectDir.resolve("src/ui/kotlin/UI.kt")

        /* Test reloading by replacing the 'Hello' string in UI.kt */
        uiKt.replaceText("""Hello""", """After""")
        requestAndAwaitReload()
        orchestration.update(VirtualTimeState) { VirtualTimeState(1.seconds) }
        checkScreenshot("1-after")

        uiKt.replaceText("""After""", """Bye""")
        requestAndAwaitReload()
        orchestration.update(VirtualTimeState) { VirtualTimeState(2.seconds) }
        checkScreenshot("2-bye")

        orchestration.send(ShutdownRequest("Bye"))
    }

    /**
     * Will copy the project form the 'projects' directory
     */
    class Extension : ProjectSetupExtension {
        override fun setupProject(fixture: HotReloadTestFixture, context: ExtensionContext) {
            val sourceProjectDir =
                Path("src/reloadFunctionalTest/resources/projects/${UIClassLoaderTest::class.simpleName}")
            if (!sourceProjectDir.exists()) {
                error("Missing project directory '$sourceProjectDir'")
            }

            sourceProjectDir.copyToRecursively(
                fixture.projectDir.path,
                followLinks = false,
                copyAction = { src, target ->
                    if (src.name == "settings.gradle.kts") return@copyToRecursively CopyActionResult.CONTINUE
                    if (src.name.startsWith(".")) return@copyToRecursively CopyActionResult.SKIP_SUBTREE
                    if (src.name == "build") return@copyToRecursively CopyActionResult.SKIP_SUBTREE

                    if (src.isDirectory()) {
                        if (target.isDirectory()) return@copyToRecursively CopyActionResult.CONTINUE
                        else target.createDirectory()
                    }

                    if (src.isRegularFile()) {
                        src.copyTo(target, StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING)
                    }

                    CopyActionResult.CONTINUE
                })
        }
    }
}
