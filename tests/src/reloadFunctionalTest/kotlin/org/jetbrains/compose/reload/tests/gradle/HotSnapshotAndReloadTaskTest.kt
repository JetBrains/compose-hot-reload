/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.tests.gradle

import kotlinx.coroutines.flow.toList
import org.jetbrains.compose.reload.core.HotReloadProperty
import org.jetbrains.compose.reload.core.PidFileInfo
import org.jetbrains.compose.reload.core.getOrThrow
import org.jetbrains.compose.reload.gradle.readObject
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ReloadClassesRequest
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ReloadClassesRequest.ChangeType.Added
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ReloadClassesRequest.ChangeType.Modified
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ShutdownRequest
import org.jetbrains.compose.reload.test.gradle.ApplicationLaunchMode
import org.jetbrains.compose.reload.test.gradle.ExtendGradleProperties
import org.jetbrains.compose.reload.test.gradle.GradlePropertiesExtension
import org.jetbrains.compose.reload.test.gradle.GradleRunner
import org.jetbrains.compose.reload.test.gradle.HotReloadTest
import org.jetbrains.compose.reload.test.gradle.HotReloadTestFixture
import org.jetbrains.compose.reload.test.gradle.ProjectMode
import org.jetbrains.compose.reload.test.gradle.TestedLaunchMode
import org.jetbrains.compose.reload.test.gradle.TestedProjectMode
import org.jetbrains.compose.reload.test.gradle.assertExitCode
import org.jetbrains.compose.reload.test.gradle.assertNoStatus
import org.jetbrains.compose.reload.test.gradle.assertSuccessful
import org.jetbrains.compose.reload.test.gradle.assertTaskExecuted
import org.jetbrains.compose.reload.test.gradle.assertTaskUpToDate
import org.jetbrains.compose.reload.test.gradle.buildFlow
import org.jetbrains.compose.reload.test.gradle.checkScreenshot
import org.jetbrains.compose.reload.test.gradle.getDefaultMainKtSourceFile
import org.jetbrains.compose.reload.test.gradle.initialSourceCode
import org.jetbrains.compose.reload.test.gradle.replaceSourceCode
import org.jetbrains.compose.reload.utils.GradleIntegrationTest
import org.jetbrains.compose.reload.utils.QuickTest
import org.junit.jupiter.api.extension.ExtensionContext
import java.util.concurrent.TimeUnit
import kotlin.io.path.deleteExisting
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.writeText
import kotlin.jvm.optionals.getOrNull
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

@QuickTest
@TestedLaunchMode(ApplicationLaunchMode.Detached)
@TestedProjectMode(ProjectMode.Kmp)
@GradleIntegrationTest
@ExtendGradleProperties(HotSnapshotAndReloadTaskTest.Extension::class)
class HotSnapshotAndReloadTaskTest {

    private val HotReloadTestFixture.snapshotFile get() = projectDir.resolve("build/run/jvmMain/classpath/.snapshot")
    private val HotReloadTestFixture.requestFile get() = projectDir.resolve("build/run/jvmMain/classpath/.request")
    private val HotReloadTestFixture.pidFile get() = projectDir.resolve("build/run/jvmMain/jvmMain.pid")
    private val snapshotTask = ":hotSnapshotJvmMain"
    private val reloadTask = ":hotReloadJvmMain"


    @HotReloadTest
    fun `test - simple up-to-date`(fixture: HotReloadTestFixture) = fixture.runTest {
        val mainFile = projectDir.resolve(getDefaultMainKtSourceFile())

        if (snapshotFile.exists()) fail("Expected '$snapshotFile' to not exist yet")
        if (requestFile.exists()) fail("Expected '$requestFile' to not exist yet")

        fixture initialSourceCode """
            import org.jetbrains.compose.reload.test.*
            // <A>
            fun main() {
                screenshotTestApplication {
                }
            }
        """.trimIndent()

        if (!snapshotFile.isRegularFile()) fail("Expected '$snapshotFile' to be present")
        if (requestFile.exists()) fail("Expected '$requestFile' to not exist yet")

        /* Initial Run w/o any changes: Expected to be UP-TO-DATE */
        fixture.gradleRunner.buildFlow(snapshotTask).toList()
            .assertSuccessful()
            .assertTaskUpToDate(snapshotTask)
        if (!snapshotFile.isRegularFile()) fail("Expected '$snapshotFile' to be present")
        if (requestFile.exists()) fail("Expected '$requestFile' to not exist after up-to-date snapshot")

        /* Change code meaningfully, expect a successful snapshot */
        fixture.replaceSourceCode("// <A>", """fun foo() = "foo"""")
        fixture.gradleRunner.buildFlow(snapshotTask).toList()
            .assertSuccessful()
            .assertTaskExecuted(snapshotTask)

        if (!requestFile.isRegularFile()) fail("Expected '$requestFile' to be present")
        val request1 = requestFile.readObject<ReloadClassesRequest>()
        if (request1.changedClassFiles.isEmpty()) fail("'changedClassFiles' is empty")
        request1.changedClassFiles.forEach { (file, changeType) ->
            assertEquals(Modified, changeType, "$file unexpected changeType")
            if ("MainKt" !in file.name) fail("Unexpected file: $file")
        }

        /* Run again, expect 'UP-TO-DATE' */
        fixture.gradleRunner.buildFlow(snapshotTask).toList()
            .assertSuccessful()
            .assertTaskUpToDate(snapshotTask)
        assertEquals(request1, requestFile.readObject<ReloadClassesRequest>())

        /* Create new class, expect pending request to contain this additionally */
        mainFile.resolveSibling("Bar.kt").writeText("""class Bar""")
        fixture.gradleRunner.buildFlow(snapshotTask).toList()
            .assertSuccessful()
            .assertTaskExecuted(snapshotTask)

        val request2 = requestFile.readObject<ReloadClassesRequest>()
        val barEntry = request2.changedClassFiles.entries.find { (file, _) -> file.name == "Bar.class" }
        if (barEntry == null) fail("Expected 'Bar.class' to be added to pending request")
        assertEquals(Added, barEntry.value)
        assertEquals(
            request2.changedClassFiles, request1.changedClassFiles.plus(barEntry.key to barEntry.value),
            "Expected pending request to contain previous changes and the newly added Bar.class"
        )
    }

    @HotReloadTest
    fun `test - consuming the pending request file`(fixture: HotReloadTestFixture) = fixture.runTest {
        fixture initialSourceCode """
            import org.jetbrains.compose.reload.test.*
            // <A>
            fun main() {
                screenshotTestApplication {
                }
            }
        """.trimIndent()

        assertTrue(snapshotFile.exists(), "Expected '$snapshotFile' to exist")
        assertFalse(requestFile.exists(), "Expected '$requestFile' to not exist yet")

        /* Modify Main.kt and produce the first initial request */
        replaceSourceCode("// <A>", """fun foo() = "foo"""")
        Thread.sleep(1000)
        fixture.gradleRunner.buildFlow(snapshotTask).toList()
            .assertSuccessful()
            .assertTaskExecuted(snapshotTask)
        val request1 = requestFile.readObject<ReloadClassesRequest>()

        /* Add a new class without 'consuming' the request */
        val barKt = projectDir.resolve(getDefaultMainKtSourceFile()).resolveSibling("Bar.kt")
        barKt.writeText("""class Bar""")
        fixture.gradleRunner.buildFlow(snapshotTask).toList()
            .assertSuccessful()
            .assertTaskExecuted(snapshotTask)
        val request2 = requestFile.readObject<ReloadClassesRequest>()
        assertTrue(
            request2.changedClassFiles.entries.containsAll(request1.changedClassFiles.entries),
            "Expected changes from request1 to be present in request2"
        )

        /* 'Consume' the request; The task is up-to-date without changing code further */
        requestFile.deleteExisting()
        fixture.gradleRunner.buildFlow(snapshotTask).toList()
            .assertSuccessful()
            .assertTaskUpToDate(snapshotTask)
        if (requestFile.exists()) fail("Expected '$requestFile' to not exist after up-to-date snapshot")

        /* Add one more class, expect a new, fresh request */
        barKt.resolveSibling("Foo.kt").writeText("""class Foo""")
        fixture.gradleRunner.buildFlow(snapshotTask).toList()
            .assertSuccessful()
            .assertTaskExecuted(snapshotTask)
        val request3 = requestFile.readObject<ReloadClassesRequest>()
        if (request3.changedClassFiles.size != 1) fail("Expected exactly one file to be changed")
        request3.changedClassFiles.run {
            val (file, changeType) = entries.single()
            assertEquals(Added, changeType, "$file unexpected changeType")
            assertEquals("Foo.class", file.name, "Unexpected file: $file")
        }
    }

    @HotReloadTest
    fun `test - compilation error`(fixture: HotReloadTestFixture) = fixture.runTest {
        fixture initialSourceCode """
            import org.jetbrains.compose.reload.test.*
            // <A>
            fun main() {
                screenshotTestApplication {
                }
            }
        """.trimIndent()

        fixture.gradleRunner.buildFlow(snapshotTask).toList()
            .assertSuccessful()
            .assertTaskUpToDate(snapshotTask)

        /* Provoke compilation error */
        replaceSourceCode("// <A>", """{error}""")
        fixture.gradleRunner.buildFlow(snapshotTask).toList()
            .assertExitCode(GradleRunner.ExitCode.failure)
            .assertNoStatus(snapshotTask)

        if (!snapshotFile.exists()) fail("Expected '$snapshotFile' to exist")
        if (requestFile.exists()) fail("Expected '$requestFile' to not exist")

        /* Fix compilation and check consume the request */
        replaceSourceCode("{error}", """fun foo() = "foo"""")
        fixture.gradleRunner.buildFlow("reload").toList()
            .assertSuccessful()
            .assertTaskExecuted(snapshotTask)
            .assertTaskExecuted(reloadTask)

        /* Try reloading again, expect 'UP-TO-DATE' on the snapshot; the reload task is allowed to run */
        fixture.gradleRunner.buildFlow("reload").toList()
            .assertSuccessful()
            .assertTaskUpToDate(snapshotTask)
            .assertTaskExecuted(reloadTask)
    }

    @HotReloadTest
    fun `test - snapshot and request are deleted after application exit`(
        fixture: HotReloadTestFixture
    ) = fixture.runTest {
        fixture initialSourceCode """
            import org.jetbrains.compose.reload.test.*
            fun main() {
                screenshotTestApplication {
                    // <A>
                }
            }
        """.trimIndent()

        /* Do a simple reload */
        replaceSourceCode("// <A>", """TestText("A")""")
        gradleRunner.buildFlow("reload").toList().assertSuccessful()
        checkScreenshot("a")
        if (!snapshotFile.exists()) fail("Expected '$snapshotFile' to exist")
        if (requestFile.exists()) fail("Expected '$requestFile' to not exist, as consumed by the reload")

        /* Provoke a 'stale request' file by only executing the snapshot, but not the reload */
        replaceSourceCode(""""A"""", """"B"""")
        gradleRunner.buildFlow(snapshotTask).toList()
            .assertSuccessful().assertTaskExecuted(snapshotTask)
        if (!requestFile.exists()) fail("Expected '$requestFile' to exist, as not consumed by any reload")

        /* Shutdown Application */
        val pid = PidFileInfo(pidFile).getOrThrow().pid ?: fail("Missing 'pid'")
        val processHandle = ProcessHandle.of(pid).getOrNull() ?: fail("Process with pid=$pid not found")
        sendMessage(ShutdownRequest("Explicitly requested by the test")) {
            processHandle.onExit().get(15, TimeUnit.SECONDS)
        }

        /* Launch Application again */
        runTransaction {
            launchApplicationAndWait()
        }
        checkScreenshot("b")
        if (requestFile.exists()) fail("Expected '$requestFile' to be deleted after application restart")


        /* Add a new class, ensure that the previous 'stale request' is not leaking */
        projectDir.resolve(getDefaultMainKtSourceFile())
            .resolveSibling("Bar.kt")
            .writeText("""class Bar""")


        val request = runTransaction {
            fixture.gradleRunner.buildFlow("reload").toList()
                .assertTaskExecuted(snapshotTask)
                .assertTaskExecuted(reloadTask)
                .assertSuccessful()

            skipToMessage<ReloadClassesRequest>()
        }

        assertEquals(
            mapOf("Bar.class" to Added),
            request.changedClassFiles.mapKeys { it.key.name }
        )
    }

    class Extension : GradlePropertiesExtension {
        override fun properties(context: ExtensionContext): List<String> {
            return listOf("${HotReloadProperty.GradleBuildContinuous.key}=false")
        }
    }
}
