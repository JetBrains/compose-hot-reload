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
import org.jetbrains.compose.devtools.states.UINotification
import org.jetbrains.compose.devtools.states.UINotificationType
import org.jetbrains.compose.reload.core.getBlocking
import org.jetbrains.compose.reload.core.getOrThrow
import org.jetbrains.compose.reload.core.launchTask
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

abstract class DevToolsUiTestBase {
    protected val events = Events()
    protected val states = States()

    // wrappers for onNodeWithTag that change the default value for `useUnmergedTree`
    // we need `useUnmergedTree = true` to prevent flakiness in tests
    protected fun ComposeUiTest.onNodeWithTag(
        tag: Tag,
        useUnmergedTree: Boolean = true,
    ) = onNodeWithTag(tag.name, useUnmergedTree = useUnmergedTree)

    protected fun ComposeUiTest.onAllNodesWithTag(
        tag: Tag,
        useUnmergedTree: Boolean = true,
    ) = onAllNodesWithTag(tag.name, useUnmergedTree = useUnmergedTree)

    protected fun ComposeUiTest.awaitNodeWithTag(
        tag: Tag,
        useUnmergedTree: Boolean = true,
        timeout: Duration = 10.seconds,
        pollingInterval: Duration = 200.milliseconds,
    ): SemanticsNodeInteraction = launchTask {
        while (onAllNodesWithTag(tag.name, useUnmergedTree).fetchSemanticsNodes().isEmpty()) {
            delay(pollingInterval)
        }
        onNodeWithTag(tag, useUnmergedTree)
    }.getBlocking(timeout = timeout).getOrThrow()
}

abstract class DevToolsUiTest : DevToolsUiTestBase() {
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
}

abstract class ParameterizedDevToolsUiTest<T> : DevToolsUiTestBase() {
    @Composable
    abstract fun content(param: T)


    @OptIn(ExperimentalTestApi::class)
    fun runSidecarUiTest(param: T, block: ComposeUiTest.() -> Unit) = runComposeUiTest {
        setContent {
            installEvas(events, states) {
                content(param)
            }
        }

        block()
    }
}

internal fun TestNotification(
    type: UINotificationType = UINotificationType.INFO,
    title: String = "Title",
    message: String = "Message",
    isDisposableFromUI: Boolean = true,
    details: List<String> = emptyList(),
) : UINotification = UINotification(type, title, message, isDisposableFromUI, details)