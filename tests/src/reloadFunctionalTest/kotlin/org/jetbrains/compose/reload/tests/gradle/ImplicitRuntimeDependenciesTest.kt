/*
 * Copyright 2024-2026 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.tests.gradle

import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import org.jetbrains.compose.reload.test.gradle.ApplicationLaunchMode
import org.jetbrains.compose.reload.test.gradle.Headless
import org.jetbrains.compose.reload.test.gradle.HotReloadTest
import org.jetbrains.compose.reload.test.gradle.HotReloadTestFixture
import org.jetbrains.compose.reload.test.gradle.OverrideBuildGradleKts
import org.jetbrains.compose.reload.test.gradle.ProjectMode
import org.jetbrains.compose.reload.test.gradle.ReloadEffects
import org.jetbrains.compose.reload.test.gradle.TestedLaunchMode
import org.jetbrains.compose.reload.test.gradle.TestedProjectMode
import org.jetbrains.compose.reload.test.gradle.getDefaultMainKtSourceFile
import org.jetbrains.compose.reload.test.gradle.launchApplication
import org.jetbrains.compose.reload.utils.QuickTest
import kotlin.io.path.createParentDirectories
import kotlin.io.path.writeText
import kotlin.test.fail

/**
 * These tests ensure that hot reload agent/jvm runtime do not have any implicit dependencies.
 *
 * This test will launch an empty application, but in *non-headless* mode.
 * Because the test is started non-headless, we can expect the in-app reload effects to be launched
 * and ensure that the application is started successfully.
 */
@QuickTest
@TestedLaunchMode(ApplicationLaunchMode.Detached)
@OverrideBuildGradleKts(BuildGradleKtsWithoutImplicitDependencies::class)
class ImplicitRuntimeDependenciesTest {

    @Headless(false)
    @ReloadEffects
    @HotReloadTest
    @TestedProjectMode(ProjectMode.Jvm)
    fun `test - jvm implicit dependencies`(fixture: HotReloadTestFixture) = fixture.runTest {
        fixture.checkImplicitRuntimeClasspath()
    }

    @Headless(false)
    @ReloadEffects
    @HotReloadTest
    @TestedProjectMode(ProjectMode.Kmp)
    fun `test - kmp implicit dependencies`(fixture: HotReloadTestFixture) = fixture.runTest {
        fixture.checkImplicitRuntimeClasspath()
    }
}

private suspend fun HotReloadTestFixture.checkImplicitRuntimeClasspath(
    projectPath: String = ""
) {
    return runTransaction {
        this@checkImplicitRuntimeClasspath.projectDir.subproject(projectPath)
            .resolve(this@checkImplicitRuntimeClasspath.getDefaultMainKtSourceFile())
            .createParentDirectories().writeText(
                """
            import androidx.compose.ui.window.singleWindowApplication

            fun main() = singleWindowApplication {}
""".trimIndent()
            )
        try {
            this@checkImplicitRuntimeClasspath.launchApplication(":$projectPath")
            skipToMessage<OrchestrationMessage>("Waiting for application to start") { message ->
                when (message) {
                    is OrchestrationMessage.UIRendered -> return@skipToMessage true
                    is OrchestrationMessage.UIException -> fail("Application failed to start: ${message.message}")
                    else -> return@skipToMessage false
                }
            }
        } finally {
            OrchestrationMessage.ShutdownRequest("Explicitly requested by the test").send()
        }
    }
}
