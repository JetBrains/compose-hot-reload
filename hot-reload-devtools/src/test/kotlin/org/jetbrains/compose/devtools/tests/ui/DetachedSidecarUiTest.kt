/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
@file:OptIn(ExperimentalTestApi::class)

package org.jetbrains.compose.devtools.tests.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onChild
import org.jetbrains.compose.devtools.Tag
import org.jetbrains.compose.devtools.sidecar.DtDetachedSidecarContent
import org.jetbrains.compose.devtools.states.BuildSystemUIState
import org.jetbrains.compose.devtools.states.ErrorUIState
import org.jetbrains.compose.devtools.states.NotificationsUIState
import org.jetbrains.compose.devtools.states.ReloadCountUIState
import org.jetbrains.compose.devtools.states.ReloadUIState
import org.jetbrains.compose.devtools.states.UIErrorDescription
import org.jetbrains.compose.devtools.states.UINotificationType
import org.jetbrains.compose.devtools.theme.DtImages
import org.jetbrains.compose.reload.core.BuildSystem
import org.jetbrains.compose.reload.core.WindowId
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class DetachedSidecarUiTest : DevToolsUiTest() {
    @Composable
    override fun content() {
        DtDetachedSidecarContent()
    }

    @Test
    fun `test - logo clickable`() = runSidecarUiTest {
        // images are loaded asynchronously, so it may not be loaded at the start of the test
        // wait fot node to be loaded before performing checks
        awaitNodeWithTag(Tag.HotReloadLogo)
            .assertExists()
            .assertHasNoClickAction()
    }

    @Test
    fun `test - reload counter`() = runSidecarUiTest {
        onNodeWithTag(Tag.ReloadCounterText).assertDoesNotExist()

        updateStateAndWaitForIdle(ReloadCountUIState.Key) { ReloadCountUIState(1) }
        awaitNodeWithTag(Tag.ReloadCounterText).assertTextContains("1", substring = true)

        updateStateAndWaitForIdle(ReloadCountUIState.Key) { ReloadCountUIState(2) }
        awaitNodeWithTag(Tag.ReloadCounterText).assertTextContains("2", substring = true)
    }

    @Test
    fun `test - reload status`() = runSidecarUiTest {
        updateStateAndWaitForIdle(ReloadUIState.Key) { ReloadUIState.Ok() }
        awaitNodeWithTag(Tag.ReloadStatusSymbol).assertExists()
            .assertContentDescriptionContains("Success")
        awaitNodeWithTag(Tag.ReloadStatusText).assertExists()
            .assertTextContains("Success", substring = true)

        updateStateAndWaitForIdle(ReloadUIState.Key) { ReloadUIState.Failed("Oh-oh") }
        awaitNodeWithTag(Tag.ReloadStatusSymbol).assertExists()
            .assertContentDescriptionContains("Error")
        awaitNodeWithTag(Tag.ReloadStatusText).assertExists()
            .assertTextContains("Failed", substring = true)
            .assertTextContains("Oh-oh", substring = true)


        updateStateAndWaitForIdle(ReloadUIState.Key) { ReloadUIState.Reloading() }
        assertEquals(
            awaitNodeWithTag(Tag.ReloadStatusSymbol).assertExists()
                .fetchSemanticsNode().config.getOrNull(SemanticsProperties.ProgressBarRangeInfo),
            ProgressBarRangeInfo.Indeterminate
        )
        awaitNodeWithTag(Tag.ReloadStatusText).assertExists()
            .assertTextContains("Reloading", substring = true)

        onNodeWithTag(Tag.BuildSystemLogo).assertDoesNotExist()

        updateStateAndWaitForIdle(BuildSystemUIState.Key) { BuildSystemUIState(BuildSystem.Gradle) }
        awaitNodeWithTag(Tag.BuildSystemLogo)
            .assertExists()
            .assertContentDescriptionContains(DtImages.Image.GRADLE_LOGO.description)

        updateStateAndWaitForIdle(BuildSystemUIState.Key) { BuildSystemUIState(BuildSystem.Amper) }
        awaitNodeWithTag(Tag.BuildSystemLogo)
            .assertExists()
            .assertContentDescriptionContains(DtImages.Image.AMPER_LOGO.description)
    }

    @Test
    fun `test - error status`() = runSidecarUiTest {
        onNodeWithTag(Tag.RuntimeErrorSymbol).assertDoesNotExist()
        onNodeWithTag(Tag.RuntimeErrorText).assertDoesNotExist()

        updateStateAndWaitForIdle(ErrorUIState.Key) {
            ErrorUIState(
                mapOf(
                    WindowId.create() to UIErrorDescription(
                        title = "Uh-oh", message = "Something went wrong", listOf()
                    )
                )
            )
        }

        awaitNodeWithTag(Tag.RuntimeErrorSymbol).assertExists()
        awaitNodeWithTag(Tag.RuntimeErrorText).assertExists()
            .assertTextContains("Uh-oh", substring = true)
            .assertTextContains("Something went wrong", substring = true)
    }

    @Test
    fun `test - actions`() = runSidecarUiTest {
        onAllNodesWithTag(Tag.ActionButton)
            .assertCountEquals(3)
            .filter(hasClickAction())
            .assertCountEquals(3)
    }

    @Test
    fun `test - notifications`() = runSidecarUiTest {
        onNodeWithTag(Tag.NotificationsButton).assertDoesNotExist()

        updateStateAndWaitForIdle(NotificationsUIState.Key) {
            NotificationsUIState(
                listOf(TestNotification(UINotificationType.INFO))
            )
        }
        awaitNodeWithTag(Tag.NotificationsButton)
            .assertHasClickAction()
            .onChild()
            .assertContentDescriptionContains(DtImages.Image.INFO_ICON.description)

        updateStateAndWaitForIdle(NotificationsUIState.Key) {
            NotificationsUIState(
                listOf(TestNotification(UINotificationType.WARNING))
            )
        }
        awaitNodeWithTag(Tag.NotificationsButton)
            .assertHasClickAction()
            .onChild()
            .assertContentDescriptionContains(DtImages.Image.WARNING_ICON.description)

        updateStateAndWaitForIdle(NotificationsUIState.Key) {
            NotificationsUIState(
                listOf(TestNotification(UINotificationType.ERROR))
            )
        }
        awaitNodeWithTag(Tag.NotificationsButton)
            .assertHasClickAction()
            .onChild()
            .assertContentDescriptionContains(DtImages.Image.ERROR_ICON.description)
    }
}
