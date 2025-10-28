/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
@file:OptIn(ExperimentalTestApi::class)

package org.jetbrains.compose.devtools.tests.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onChild
import androidx.compose.ui.test.onParent
import org.jetbrains.compose.devtools.Tag
import org.jetbrains.compose.devtools.sidecar.DtSidecarWindowContent
import org.jetbrains.compose.devtools.sidecar.devToolsUseTransparency
import org.jetbrains.compose.devtools.states.BuildSystemUIState
import org.jetbrains.compose.devtools.states.NotificationsUIState
import org.jetbrains.compose.devtools.states.ReloadCountUIState
import org.jetbrains.compose.devtools.states.ReloadUIState
import org.jetbrains.compose.devtools.states.UINotificationType
import org.jetbrains.compose.devtools.theme.DtImages
import org.jetbrains.compose.reload.core.BuildSystem
import org.junit.jupiter.api.Test

class SidecarUiTest : DevToolsUiTest() {

    @Composable
    override fun content() {
        DtSidecarWindowContent()
    }

    @Test
    fun `test - logo clickable`() = runSidecarUiTest {
        awaitNodeWithTag(Tag.HotReloadLogo)
            .assertExists()
            .onParent()
            .assertHasNoClickAction()
    }

    @Test
    fun `test - reload counter`() = runSidecarUiTest {
        if (devToolsUseTransparency) {
            onNodeWithTag(Tag.ReloadCounterText).assertDoesNotExist()
        } else {
            awaitNodeWithTag(Tag.ReloadCounterText).assertTextContains("0", substring = true)
        }

        states.updateState(ReloadCountUIState.Key) { ReloadCountUIState(1) }
        awaitNodeWithTag(Tag.ReloadCounterText).assertTextContains("1", substring = true)

        states.updateState(ReloadCountUIState.Key) { ReloadCountUIState(2) }
        awaitNodeWithTag(Tag.ReloadCounterText).assertTextContains("2", substring = true)
    }

    @Test
    fun `test - reload status`() = runSidecarUiTest {
        states.updateState(ReloadUIState.Key) { ReloadUIState.Reloading() }
        onNodeWithTag(Tag.BuildSystemLogo).assertDoesNotExist()

        states.updateState(BuildSystemUIState.Key) { BuildSystemUIState(BuildSystem.Gradle) }
        awaitNodeWithTag(Tag.BuildSystemLogo)
            .assertExists()
            .assertContentDescriptionContains(DtImages.Image.GRADLE_LOGO.description)

        states.updateState(BuildSystemUIState.Key) { BuildSystemUIState(BuildSystem.Amper) }
        awaitNodeWithTag(Tag.BuildSystemLogo)
            .assertExists()
            .assertContentDescriptionContains(DtImages.Image.AMPER_LOGO.description)
    }

    @Test
    fun `test - actions`() = runSidecarUiTest {
        onAllNodesWithTag(Tag.ActionButton)
            .assertCountEquals(5)
            .filter(hasClickAction())
            .assertCountEquals(5)
    }

    @Test
    fun `test - notifications`() = runSidecarUiTest {
        onNodeWithTag(Tag.NotificationsButton).assertDoesNotExist()

        states.updateState(NotificationsUIState.Key) {
            NotificationsUIState(
                listOf(TestNotification(UINotificationType.INFO))
            )
        }
        awaitNodeWithTag(Tag.NotificationsButton)
            .assertHasClickAction()
            .onChild()
            .assertContentDescriptionContains(DtImages.Image.INFO_ICON.description)

        states.updateState(NotificationsUIState.Key) {
            NotificationsUIState(
                listOf(TestNotification(UINotificationType.WARNING))
            )
        }
        awaitNodeWithTag(Tag.NotificationsButton)
            .assertHasClickAction()
            .onChild()
            .assertContentDescriptionContains(DtImages.Image.WARNING_ICON.description)

        states.updateState(NotificationsUIState.Key) {
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
