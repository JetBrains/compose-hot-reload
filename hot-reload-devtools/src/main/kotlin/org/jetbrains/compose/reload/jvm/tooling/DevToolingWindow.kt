/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.jvm.tooling

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.application
import io.sellmair.evas.compose.installEvas
import io.sellmair.evas.eventsOrThrow
import io.sellmair.evas.statesOrThrow
import org.jetbrains.compose.resources.ExperimentalResourceApi


@OptIn(ExperimentalResourceApi::class)
internal fun runDevToolingApplication() {
    application(
        exitProcessOnExit = false,
    ) {
        installEvas(
            applicationScope.coroutineContext.eventsOrThrow,
            applicationScope.coroutineContext.statesOrThrow
        ) {
            MaterialTheme(
                colorScheme = MaterialTheme.colorScheme.copy(
                    primary = Color.Black,
                )
            ) {
                DevOverlays()
            }
        }
    }
}
