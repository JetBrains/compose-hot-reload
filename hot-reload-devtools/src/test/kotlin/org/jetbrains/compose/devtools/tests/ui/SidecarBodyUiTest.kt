/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
@file:OptIn(ExperimentalTestApi::class)

package org.jetbrains.compose.devtools.tests.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.runComposeUiTest
import io.sellmair.evas.Events
import io.sellmair.evas.States
import io.sellmair.evas.compose.installEvas
import kotlinx.coroutines.delay
import org.jetbrains.compose.devtools.Tag
import org.jetbrains.compose.reload.core.launchTask
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

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

    @OptIn(ExperimentalTestApi::class)
    protected suspend fun ComposeUiTest.awaitNodeWithTag(
        tag: Tag,
        delay: Duration = 200.milliseconds,
        useUnmergedTree: Boolean = false,
    ) {
        launchTask {
            while (onAllNodesWithTag(tag.name, useUnmergedTree).fetchSemanticsNodes().isEmpty()) {
                delay(delay)
            }
        }.await()
    }
}
