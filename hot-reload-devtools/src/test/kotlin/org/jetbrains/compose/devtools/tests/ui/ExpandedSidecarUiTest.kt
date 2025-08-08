/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
@file:OptIn(ExperimentalTestApi::class)

package org.jetbrains.compose.devtools.tests.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import org.jetbrains.compose.devtools.Tag
import org.jetbrains.compose.devtools.sidecar.DtExpandedSidecarWindowContent
import org.jetbrains.compose.devtools.sidecar.devToolsUseTransparency
import org.jetbrains.compose.devtools.states.BuildSystemState
import org.jetbrains.compose.devtools.states.ReloadCountState
import org.jetbrains.compose.devtools.states.ReloadState
import org.jetbrains.compose.devtools.states.UIErrorDescription
import org.jetbrains.compose.devtools.states.UIErrorState
import org.jetbrains.compose.devtools.theme.DtImages
import org.jetbrains.compose.reload.core.BuildSystem
import org.jetbrains.compose.reload.core.WindowId
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ExpandedSidecarUiTest : SidecarBodyUiTest() {

    @Composable
    override fun content() {
        var isExpanded by remember { mutableStateOf(true) }
        DtExpandedSidecarWindowContent(
            isExpanded,
            isExpandedChanged = { isExpanded = it }
        )
    }

    @Test
    fun `test - logo clickable`() = runSidecarUiTest {
        // images are loaded asynchronously, so it may not be loaded at the start of the test
        // wait for the node to be loaded before performing checks
        awaitNodeWithTag(Tag.HotReloadLogo)
            .assertExists()
            .assertHasNoClickAction()
    }

    @Test
    fun `test - reload counter`() = runSidecarUiTest {
        onNodeWithTag(Tag.ReloadCounterText).assertDoesNotExist()

        states.updateState(ReloadCountState.Key) { ReloadCountState(1) }
        onNodeWithTag(Tag.ReloadCounterText).assertTextContains("1", substring = true)

        states.updateState(ReloadCountState.Key) { ReloadCountState(2) }
        onNodeWithTag(Tag.ReloadCounterText).assertTextContains("2", substring = true)
    }

    @Test
    fun `test - reload status`() = runSidecarUiTest {
        states.updateState(ReloadState.Key) { ReloadState.Ok() }
        onNodeWithTag(Tag.ReloadStatusSymbol).assertExists()
            .assertContentDescriptionContains("Success")
        onNodeWithTag(Tag.ReloadStatusText).assertExists()
            .assertTextContains("Success", substring = true)

        states.updateState(ReloadState.Key) { ReloadState.Failed("Oh-oh") }
        onNodeWithTag(Tag.ReloadStatusSymbol).assertExists()
            .assertContentDescriptionContains("Error")
        onNodeWithTag(Tag.ReloadStatusText).assertExists()
            .assertTextContains("Failed", substring = true)
            .assertTextContains("Oh-oh", substring = true)


        states.updateState(ReloadState.Key) { ReloadState.Reloading() }
        assertEquals(
            onNodeWithTag(Tag.ReloadStatusSymbol).assertExists()
                .fetchSemanticsNode().config.getOrNull(SemanticsProperties.ProgressBarRangeInfo),
            ProgressBarRangeInfo.Indeterminate
        )
        onNodeWithTag(Tag.ReloadStatusText).assertExists()
            .assertTextContains("Reloading", substring = true)

        onNodeWithTag(Tag.BuildSystemLogo).assertDoesNotExist()

        states.updateState(BuildSystemState.Key) { BuildSystemState(BuildSystem.Gradle) }
        awaitNodeWithTag(Tag.BuildSystemLogo)
            .assertExists()
            .assertContentDescriptionContains(DtImages.Image.GRADLE_LOGO.name)

        states.updateState(BuildSystemState.Key) { BuildSystemState(BuildSystem.Amper) }
        awaitNodeWithTag(Tag.BuildSystemLogo)
            .assertExists()
            .assertContentDescriptionContains(DtImages.Image.AMPER_LOGO.name)

    }

    @Test
    fun `test - error status`() = runSidecarUiTest {
        onNodeWithTag(Tag.RuntimeErrorSymbol).assertDoesNotExist()
        onNodeWithTag(Tag.RuntimeErrorText).assertDoesNotExist()

        states.updateState(UIErrorState.Key) {
            UIErrorState(
                mapOf(
                    WindowId.create() to UIErrorDescription(
                        title = "Uh-oh", message = "Something went wrong", listOf()
                    )
                )
            )
        }

        onNodeWithTag(Tag.RuntimeErrorSymbol).assertExists()
        onNodeWithTag(Tag.RuntimeErrorText).assertExists()
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
    fun `test - expand minimise`() = runSidecarUiTest {
        // minimise the window
        onAllNodesWithTag(Tag.ExpandMinimiseButton)
            .assertCountEquals(1)
            .onFirst()
            .assertHasClickAction()
            .performClick()

        // assert that the expanded UI elements are not visible
        onAllNodesWithTag(Tag.ActionButton)
            .assertCountEquals(0)

        onNodeWithTag(Tag.Console)
            .assertDoesNotExist()

        if (devToolsUseTransparency) {
            onNodeWithTag(Tag.ReloadCounterText)
                .assertDoesNotExist()
        } else {
            onNodeWithTag(Tag.ReloadCounterText)
                .assertTextContains("0", substring = true)
        }

        onNodeWithTag(Tag.ReloadStatusText)
            .assertDoesNotExist()

        states.updateState(ReloadCountState.Key) { ReloadCountState(1) }

        onNodeWithTag(Tag.ReloadCounterText)
            .assertTextContains("1", substring = true)

        // maximise the window
        onAllNodesWithTag(Tag.ExpandMinimiseButton)
            .assertCountEquals(1)
            .onFirst()
            .assertHasClickAction()
            .performClick()

        onNodeWithTag(Tag.ReloadCounterText)
            .assertTextContains("1", substring = true)
    }
}
