/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.devtools.tests.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import io.sellmair.evas.Events
import io.sellmair.evas.States
import io.sellmair.evas.compose.installEvas
import org.jetbrains.compose.devtools.sidecar.DtMinimizedSidecarWindowContent

abstract class SidecarBodyUiTest {
    protected val events = Events()
    protected val states = States()


    @Composable
    abstract fun content(): Unit


    @OptIn(ExperimentalTestApi::class)
    fun runSidecarUiTest(block: ComposeUiTest.() -> Unit) = runComposeUiTest {
        setContent {
            installEvas(events, states) {
                content()
            }
        }

        block()
    }
}
