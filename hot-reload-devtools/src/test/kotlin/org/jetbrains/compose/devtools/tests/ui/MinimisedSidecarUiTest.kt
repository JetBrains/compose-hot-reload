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
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onParent
import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.devtools.Tag
import org.jetbrains.compose.devtools.sidecar.DtMinimizedSidecarWindowContent
import org.jetbrains.compose.devtools.states.BuildSystemState
import org.jetbrains.compose.devtools.states.ReloadCountState
import org.jetbrains.compose.devtools.states.ReloadState
import org.jetbrains.compose.devtools.theme.DtLogos
import org.jetbrains.compose.reload.core.BuildSystem
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

class MinimisedSidecarUiTest : SidecarBodyUiTest() {

    @Composable
    override fun content() {
        DtMinimizedSidecarWindowContent()
    }

    @Test
    fun `test - logo clickable`() = runSidecarUiTest {
        onNodeWithTag(Tag.HotReloadLogo.name, useUnmergedTree = true)
            .assertExists()
            .onParent()
            .assertHasClickAction()

        onAllNodesWithTag(Tag.ExpandMinimiseButton.name, useUnmergedTree = true)
            .assertCountEquals(1)
            .onFirst()
            .assertHasClickAction()
    }

    @Test
    fun `test - reload counter`() = runSidecarUiTest {
        onNodeWithTag(Tag.ReloadCounterText.name, useUnmergedTree = true).assertDoesNotExist()

        states.updateState(ReloadCountState.Key) { ReloadCountState(1) }
        onNodeWithTag(Tag.ReloadCounterText.name, useUnmergedTree = true).assertTextContains("1", substring = true)

        states.updateState(ReloadCountState.Key) { ReloadCountState(2) }
        onNodeWithTag(Tag.ReloadCounterText.name, useUnmergedTree = true).assertTextContains("2", substring = true)
    }

    @Test
    fun `test - reload status`() = runSidecarUiTest {
        states.updateState(ReloadState.Key) { ReloadState.Reloading() }
        onNodeWithTag(Tag.BuildSystemLogo.name).assertDoesNotExist()

        // give 10 second timeout to load the image and display it
        runTest(timeout = 10.seconds) {
            states.updateState(BuildSystemState.Key) { BuildSystemState.Initialised(BuildSystem.Gradle) }

            awaitNodeWithTag(Tag.BuildSystemLogo, useUnmergedTree = true)

            onNodeWithTag(Tag.BuildSystemLogo.name, useUnmergedTree = true)
                .assertExists()
                .assertContentDescriptionContains(DtLogos.Image.GRADLE_LOGO.name)
        }

        runTest(timeout = 10.seconds) {
            states.updateState(BuildSystemState.Key) { BuildSystemState.Initialised(BuildSystem.Amper) }

            awaitNodeWithTag(Tag.BuildSystemLogo, useUnmergedTree = true)

            onNodeWithTag(Tag.BuildSystemLogo.name, useUnmergedTree = true)
                .assertExists()
                .assertContentDescriptionContains(DtLogos.Image.AMPER_LOGO.name)
        }
    }
}
