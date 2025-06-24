/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

@file:OptIn(DelicateHotReloadApi::class)

package org.jetbrains.compose.reload

import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

@OptIn(ExperimentalTestApi::class)
class ApiTests {

    @Test
    fun `test - jvm module is not present`() {
        assertFailsWith<ClassNotFoundException> {
            Class.forName("org.jetbrains.compose.reload.jvm.JvmDevelopmentEntryPoint")
        }
    }

    @Test
    fun `test - noop - DevelopmentEntryPoint`() = runComposeUiTest {
        setContent {
            DevelopmentEntryPoint {
                Text("Foo", modifier = Modifier.testTag("text"))
            }
        }

        onNodeWithTag("text").assertExists().assertTextEquals("Foo")
    }

    @Test
    fun `test - noop - isHotReloadActive`() {
        assertFalse(isHotReloadActive)
    }

    @Test
    fun `test - noop - AfterHotReloadEffect`() = runComposeUiTest {
        setContent {
            AfterHotReloadEffect {
                error("Should never be called")
            }

            Text("Foo", modifier = Modifier.testTag("text"))
        }

        onNodeWithTag("text").assertExists().assertTextEquals("Foo")
    }

    @Test
    fun `test - noop - staticReloadScope`() {
        staticHotReloadScope.invokeAfterHotReload { error("Should never be called") }
            .close()
    }
}
