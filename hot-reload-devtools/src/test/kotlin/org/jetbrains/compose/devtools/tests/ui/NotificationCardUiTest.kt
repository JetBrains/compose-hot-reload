/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
@file:OptIn(ExperimentalTestApi::class)

package org.jetbrains.compose.devtools.tests.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onChild
import androidx.compose.ui.test.performClick
import org.jetbrains.compose.devtools.Tag
import org.jetbrains.compose.devtools.states.UINotification
import org.jetbrains.compose.devtools.states.UINotificationType
import org.jetbrains.compose.devtools.theme.DtImages
import org.jetbrains.compose.devtools.widgets.DtNotificationCard
import kotlin.test.Test

class NotificationCardUiTest : ParameterizedDevToolsUiTest<UINotification>() {

    @Composable
    override fun content(param: UINotification) {
        DtNotificationCard(param)
    }

    @Test
    fun `test - basic notification`() = runSidecarUiTest(
        param = TestNotification(
            type = UINotificationType.INFO,
            title = "Test notification",
            message = "Test message",
            isDisposableFromUI = true,
        )
    ) {
        awaitNodeWithTag(Tag.NotificationIcon)
            .assertExists()
            .assertContentDescriptionContains(DtImages.Image.INFO_ICON.description)

        awaitNodeWithTag(Tag.NotificationTitle)
            .assertExists()
            .assertTextContains("Test notification")

        awaitNodeWithTag(Tag.NotificationMessage)
            .assertExists()
            .assertTextContains("Test message")

        awaitNodeWithTag(Tag.CopyToClipboardButton)
            .assertExists()
            .assertHasClickAction()
            .onChild()
            .assertContentDescriptionContains(DtImages.Image.COPY_ICON.description)

        awaitNodeWithTag(Tag.NotificationCleanButton)
            .assertExists()
            .assertHasClickAction()
            .onChild()
            .assertContentDescriptionContains(DtImages.Image.CLOSE_ICON.description)

        onNodeWithTag(Tag.NotificationExpandButton)
            .assertDoesNotExist()

        onNodeWithTag(Tag.Console)
            .assertDoesNotExist()
    }

    @Test
    fun `test - warning with details`() = runSidecarUiTest(
        param = TestNotification(
            type = UINotificationType.WARNING,
            title = "Test notification",
            message = "Test message",
            isDisposableFromUI = true,
            details = listOf("Test detail 1", "Test detail 2"),
        )
    ) {
        awaitNodeWithTag(Tag.NotificationIcon)
            .assertExists()
            .assertContentDescriptionContains(DtImages.Image.WARNING_ICON.description)

        awaitNodeWithTag(Tag.NotificationTitle)
            .assertExists()
            .assertTextContains("Test notification")

        awaitNodeWithTag(Tag.NotificationMessage)
            .assertExists()
            .assertTextContains("Test message")

        awaitNodeWithTag(Tag.CopyToClipboardButton)
            .assertExists()
            .assertHasClickAction()
            .onChild()
            .assertContentDescriptionContains(DtImages.Image.COPY_ICON.description)

        awaitNodeWithTag(Tag.NotificationCleanButton)
            .assertExists()
            .assertHasClickAction()
            .onChild()
            .assertContentDescriptionContains(DtImages.Image.CLOSE_ICON.description)


        awaitNodeWithTag(Tag.NotificationExpandButton)
            .assertExists()

        onNodeWithTag(Tag.Console)
            .assertDoesNotExist()

        awaitNodeWithTag(Tag.NotificationExpandButton)
            .performClick()

        awaitNodeWithTag(Tag.Console)
            .assertExists()
    }

    @Test
    fun `test - error with no dispose`() = runSidecarUiTest(
        param = TestNotification(
            type = UINotificationType.ERROR,
            title = "Test notification",
            message = "Test message",
            isDisposableFromUI = false,
        )
    ) {
        awaitNodeWithTag(Tag.NotificationIcon)
            .assertExists()
            .assertContentDescriptionContains(DtImages.Image.ERROR_ICON.description)

        awaitNodeWithTag(Tag.NotificationTitle)
            .assertExists()
            .assertTextContains("Test notification")

        awaitNodeWithTag(Tag.NotificationMessage)
            .assertExists()
            .assertTextContains("Test message")

        awaitNodeWithTag(Tag.CopyToClipboardButton)
            .assertExists()
            .assertHasClickAction()
            .onChild()
            .assertContentDescriptionContains(DtImages.Image.COPY_ICON.description)

        onNodeWithTag(Tag.NotificationCleanButton)
            .assertDoesNotExist()

        onNodeWithTag(Tag.NotificationExpandButton)
            .assertDoesNotExist()

        onNodeWithTag(Tag.Console)
            .assertDoesNotExist()
    }
}