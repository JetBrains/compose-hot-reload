/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

@file:Suppress("unused")

package org.jetbrains.compose.devtools

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import io.sellmair.evas.compose.installEvas
import io.sellmair.evas.eventsOrThrow
import io.sellmair.evas.statesOrThrow
import org.jetbrains.compose.devtools.sidecar.DtDetachedSidecarContent
import org.jetbrains.compose.reload.DevelopmentEntryPoint

@DevelopmentEntryPoint
@Composable
fun DevToolingSidecarEntryPoint() {
    LaunchedEffect(Unit) {
        setupShutdownProcedure()
        applicationScope.launchApplicationStates()
    }

    installEvas(applicationScope.coroutineContext.eventsOrThrow, applicationScope.coroutineContext.statesOrThrow) {
        DtDetachedSidecarContent()
    }
}
