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
import androidx.compose.ui.test.hasAnyChild
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.devtools.Tag
import org.jetbrains.compose.devtools.sidecar.DtDetachedSidecarContent
import org.jetbrains.compose.devtools.states.BuildSystemState
import org.jetbrains.compose.devtools.states.ReloadCountState
import org.jetbrains.compose.devtools.states.ReloadState
import org.jetbrains.compose.devtools.states.UIErrorDescription
import org.jetbrains.compose.devtools.states.UIErrorState
import org.jetbrains.compose.devtools.theme.DtLogos
import org.jetbrains.compose.reload.core.BuildSystem
import org.jetbrains.compose.reload.core.WindowId
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class DetachedSidecarUiTest : SidecarBodyUiTest() {
    @Composable
    override fun content() {
        DtDetachedSidecarContent()
    }

    @Test
    fun `test - logo clickable`() = runSidecarUiTest {
        onNodeWithTag(Tag.HotReloadLogo.name, useUnmergedTree = true)
            .assertExists()
            .assertHasNoClickAction()
    }

    @Test
    fun `test - reload counter`() = runSidecarUiTest {
        onNodeWithTag(Tag.ReloadCounterText.name).assertDoesNotExist()

        states.updateState(ReloadCountState.Key) { ReloadCountState(1) }
        onNodeWithTag(Tag.ReloadCounterText.name).assertTextContains("1", substring = true)

        states.updateState(ReloadCountState.Key) { ReloadCountState(2) }
        onNodeWithTag(Tag.ReloadCounterText.name).assertTextContains("2", substring = true)
    }

    @Test
    fun `test - reload status`() = runSidecarUiTest {
        states.updateState(ReloadState.Key) { ReloadState.Ok() }
        onNodeWithTag(Tag.ReloadStatusSymbol.name).assertExists()
            .assertContentDescriptionContains("Success")
        onNodeWithTag(Tag.ReloadStatusText.name).assertExists()
            .assertTextContains("Success", substring = true)

        states.updateState(ReloadState.Key) { ReloadState.Failed("Oh-oh") }
        onNodeWithTag(Tag.ReloadStatusSymbol.name).assertExists().assertContentDescriptionContains("Error")
        onNodeWithTag(Tag.ReloadStatusText.name).assertExists()
            .assertTextContains("Failed", substring = true)
            .assertTextContains("Oh-oh", substring = true)


        states.updateState(ReloadState.Key) { ReloadState.Reloading() }
        assertEquals(
            onNodeWithTag(Tag.ReloadStatusSymbol.name).assertExists()
                .fetchSemanticsNode().config.getOrNull(SemanticsProperties.ProgressBarRangeInfo),
            ProgressBarRangeInfo.Indeterminate
        )
        onNodeWithTag(Tag.ReloadStatusText.name).assertExists()
            .assertTextContains("Reloading", substring = true)

        onNodeWithTag(Tag.BuildSystemLogo.name).assertDoesNotExist()

        runTest {
            // preload logos so that the UI will not spend time waiting for them
            DtLogos.imageAsPngPainter(DtLogos.Image.GRADLE_LOGO).await()
            states.updateState(BuildSystemState.Key) { BuildSystemState.Initialised(BuildSystem.Gradle) }
            onNodeWithTag(Tag.BuildSystemLogo.name)
                .assertExists()
                .assertContentDescriptionContains(DtLogos.Image.GRADLE_LOGO.name)
        }

        runTest {
            // preload logos so that the UI will not spend time waiting for them
            DtLogos.imageAsPngPainter(DtLogos.Image.AMPER_LOGO).await()!!
            states.updateState(BuildSystemState.Key) { BuildSystemState.Initialised(BuildSystem.Amper) }
            onNodeWithTag(Tag.BuildSystemLogo.name)
                .assertExists()
                .assertContentDescriptionContains(DtLogos.Image.AMPER_LOGO.name)
        }

    }

    @Test
    fun `test - error status`() = runSidecarUiTest {
        onNodeWithTag(Tag.RuntimeErrorSymbol.name).assertDoesNotExist()
        onNodeWithTag(Tag.RuntimeErrorText.name).assertDoesNotExist()

        states.updateState(UIErrorState.Key) {
            UIErrorState(
                mapOf(
                    WindowId.create() to UIErrorDescription(
                        title = "Uh-oh", message = "Something went wrong", listOf()
                    )
                )
            )
        }

        onNodeWithTag(Tag.RuntimeErrorSymbol.name).assertExists()
        onNodeWithTag(Tag.RuntimeErrorText.name).assertExists()
            .assertTextContains("Uh-oh", substring = true)
            .assertTextContains("Something went wrong", substring = true)

        onNodeWithTag(Tag.RuntimeErrorText.name).assertHasClickAction()
    }

    @Test
    fun `test - actions`() = runSidecarUiTest {
        onAllNodesWithTag(Tag.ActionButton.name)
            .assertCountEquals(4)
            .filter(hasClickAction())
            .assertCountEquals(4)
    }

    @Test
    fun `test - expand minimise`() = runSidecarUiTest {
        onNodeWithTag(Tag.ExpandMinimiseButton.name, useUnmergedTree = true)
            .assertDoesNotExist()
    }
}
