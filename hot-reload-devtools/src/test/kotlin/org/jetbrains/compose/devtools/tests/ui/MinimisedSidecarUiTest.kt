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
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onParent
import org.jetbrains.compose.devtools.Tag
import org.jetbrains.compose.devtools.sidecar.DtMinimizedSidecarWindowContent
import org.jetbrains.compose.devtools.sidecar.devToolsUseTransparency
import org.jetbrains.compose.devtools.states.BuildSystemUIState
import org.jetbrains.compose.devtools.states.ReloadCountUIState
import org.jetbrains.compose.devtools.states.ReloadUIState
import org.jetbrains.compose.devtools.theme.DtImages
import org.jetbrains.compose.reload.core.BuildSystem
import org.junit.jupiter.api.Test

class MinimisedSidecarUiTest : SidecarBodyUiTest() {

    @Composable
    override fun content() {
        DtMinimizedSidecarWindowContent()
    }

    @Test
    fun `test - logo clickable`() = runSidecarUiTest {
        awaitNodeWithTag(Tag.HotReloadLogo)
            .assertExists()
            .onParent()
            .assertHasClickAction()

        onAllNodesWithTag(Tag.ExpandMinimiseButton)
            .assertCountEquals(1)
            .onFirst()
            .assertHasClickAction()
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
            .assertContentDescriptionContains(DtImages.Image.GRADLE_LOGO.name)

        states.updateState(BuildSystemUIState.Key) { BuildSystemUIState(BuildSystem.Amper) }
        awaitNodeWithTag(Tag.BuildSystemLogo)
            .assertExists()
            .assertContentDescriptionContains(DtImages.Image.AMPER_LOGO.name)
    }
}
