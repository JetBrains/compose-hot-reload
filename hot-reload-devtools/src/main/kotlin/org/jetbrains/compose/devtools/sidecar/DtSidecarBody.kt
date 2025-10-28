/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.compose.devtools.sidecar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.devtools.theme.DtPadding

@Composable
internal fun DtDetachedSidecarBody(modifier: Modifier = Modifier) {
    DtCompositionLocalProvider {
        Column(
            verticalArrangement = Arrangement.spacedBy(DtPadding.largeElementPadding),
            modifier = modifier.fillMaxSize()
                .padding(horizontal = DtPadding.borderPadding)
                .padding(bottom = DtPadding.large)
                .padding(top = DtPadding.borderPadding),
        ) {
            // Header
            DtDetachedSidecarHeaderBar()

            // Action bar with controls
            DtSidecarActionBar()

            // Status section showing the current state
            DtSidecarStatusSection()

            // Main console with logs
            DtMainConsole(modifier = Modifier.fillMaxSize())
        }
    }
}
