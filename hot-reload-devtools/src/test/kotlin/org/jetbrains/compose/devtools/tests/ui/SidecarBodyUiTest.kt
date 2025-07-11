/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
@file:OptIn(ExperimentalTestApi::class)

package org.jetbrains.compose.devtools.tests.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import io.sellmair.evas.Events
import io.sellmair.evas.States
import io.sellmair.evas.compose.installEvas
import kotlinx.coroutines.delay
import org.jetbrains.compose.devtools.Tag
import org.jetbrains.compose.reload.core.getBlocking
import org.jetbrains.compose.reload.core.getOrThrow
import org.jetbrains.compose.reload.core.launchTask
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

abstract class SidecarBodyUiTest {
    protected val events = Events()
    protected val states = States()


    @Composable
    abstract fun content()


    @OptIn(ExperimentalTestApi::class)
    fun runSidecarUiTest(block: ComposeUiTest.() -> Unit) = runComposeUiTest {
        setContent {
            installEvas(events, states) {
                content()
            }
        }

        block()
    }

    protected fun ComposeUiTest.awaitNodeWithTag(
        tag: Tag,
        useUnmergedTree: Boolean = false,
        timeout: Duration = 10.seconds,
        pollingInterval: Duration = 200.milliseconds,
    ): SemanticsNodeInteraction = launchTask {
        while (onAllNodesWithTag(tag.name, useUnmergedTree).fetchSemanticsNodes().isEmpty()) {
            delay(pollingInterval)
        }
        onNodeWithTag(tag.name, useUnmergedTree)
    }.getBlocking(timeout = timeout).getOrThrow()
}
