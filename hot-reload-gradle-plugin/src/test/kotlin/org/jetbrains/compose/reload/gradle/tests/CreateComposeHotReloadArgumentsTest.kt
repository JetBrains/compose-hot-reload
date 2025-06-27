/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.gradle.tests

import org.gradle.testfixtures.ProjectBuilder
import org.jetbrains.compose.reload.gradle.ComposeHotReloadArguments
import org.jetbrains.compose.reload.gradle.composeReloadOrchestrationPort
import org.jetbrains.compose.reload.gradle.createComposeHotReloadArguments
import kotlin.test.Test
import kotlin.test.assertEquals

class CreateComposeHotReloadArgumentsTest {
    @Test
    fun `test - defaults`() {
        val project = ProjectBuilder.builder().build()
        val arguments = project.createComposeHotReloadArguments { } as ComposeHotReloadArguments
        assertEquals(project.composeReloadOrchestrationPort, arguments.orchestrationPort)
        assertEquals(System.getProperty("java.home"), arguments.javaHome.orNull)
    }

    @Test
    fun `test - set pidFile`() {
        val project = ProjectBuilder.builder().build()
        val arguments = project.createComposeHotReloadArguments {
            setPidFile(project.layout.buildDirectory.file("foo").map { it.asFile })
        } as ComposeHotReloadArguments

        assertEquals(project.layout.buildDirectory.file("foo").map { it.asFile }.get(), arguments.pidFile.get())
    }
}
