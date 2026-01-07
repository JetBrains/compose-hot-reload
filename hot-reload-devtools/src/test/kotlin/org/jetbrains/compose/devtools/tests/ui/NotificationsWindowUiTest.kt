/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
@file:OptIn(ExperimentalTestApi::class)

package org.jetbrains.compose.devtools.tests.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import org.jetbrains.compose.devtools.Tag
import org.jetbrains.compose.devtools.sidecar.DtNotificationsWindowContent
import org.jetbrains.compose.devtools.states.NotificationsUIState
import org.jetbrains.compose.devtools.states.UINotificationType
import org.junit.jupiter.api.Test

class NotificationsWindowUiTest : DevToolsUiTest() {
    @Composable
    override fun content() {
        DtNotificationsWindowContent()
    }

    @Test
    fun `test - notifications`() = runSidecarUiTest {
        onAllNodesWithTag(Tag.NotificationCard)
            .assertCountEquals(0)

        updateStateAndWaitForIdle(NotificationsUIState.Key) {
            NotificationsUIState(
                listOf(TestNotification(UINotificationType.INFO))
            )
        }
        onAllNodesWithTag(Tag.NotificationCard)
            .assertCountEquals(1)

        updateStateAndWaitForIdle(NotificationsUIState.Key) {
            NotificationsUIState(emptyList())
        }
        onAllNodesWithTag(Tag.NotificationCard)
            .assertCountEquals(0)

        updateStateAndWaitForIdle(NotificationsUIState.Key) {
            NotificationsUIState(
                listOf(
                    TestNotification(UINotificationType.INFO),
                    TestNotification(UINotificationType.ERROR),
                )
            )
        }
        onAllNodesWithTag(Tag.NotificationCard)
            .assertCountEquals(2)
    }
}
