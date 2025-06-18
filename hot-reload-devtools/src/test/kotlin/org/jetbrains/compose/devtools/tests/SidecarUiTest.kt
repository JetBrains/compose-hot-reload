/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
@file:OptIn(ExperimentalTestApi::class)

package org.jetbrains.compose.devtools.tests

import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import io.sellmair.evas.Events
import io.sellmair.evas.States
import io.sellmair.evas.compose.installEvas
import org.jetbrains.compose.devtools.Tag
import org.jetbrains.compose.devtools.sidecar.DtExpandedSidecarWindowContent
import org.jetbrains.compose.devtools.states.ReloadCountState
import org.jetbrains.compose.devtools.states.ReloadState
import org.jetbrains.compose.devtools.states.UIErrorDescription
import org.jetbrains.compose.devtools.states.UIErrorState
import org.jetbrains.compose.reload.core.WindowId
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class SidecarUiTest {

    private val events = Events()
    private val states = States()

    @Test
    fun `test - reload counter`() = runComposeUiTest {
        setContent {
            installEvas(events, states) {
                DtExpandedSidecarWindowContent()
            }
        }

        onNodeWithTag(Tag.ReloadCounterText.name).assertDoesNotExist()

        states.updateState(ReloadCountState.Key) { ReloadCountState(1) }
        onNodeWithTag(Tag.ReloadCounterText.name).assertTextContains("1", substring = true)

        states.updateState(ReloadCountState.Key) { ReloadCountState(2) }
        onNodeWithTag(Tag.ReloadCounterText.name).assertTextContains("2", substring = true)
    }

    @Test
    fun `test - reload status`() = runComposeUiTest {
        setContent {
            installEvas(events, states) {
                DtExpandedSidecarWindowContent()
            }
        }

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

    }

    @Test
    fun `test - error status`() = runComposeUiTest {
        setContent {
            installEvas(events, states) {
                DtExpandedSidecarWindowContent()
            }
        }

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

}
