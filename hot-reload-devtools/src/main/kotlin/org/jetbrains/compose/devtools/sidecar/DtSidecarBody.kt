/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.compose.devtools.sidecar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Divider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.devtools.theme.DtColors
import org.jetbrains.compose.devtools.theme.DtPadding
import org.jetbrains.compose.devtools.widgets.animateReloadStatusColor

@Composable
internal fun DtSidecarBody(modifier: Modifier = Modifier) {
    Column(
        verticalArrangement = Arrangement.spacedBy(DtPadding.large),
        modifier = modifier.fillMaxSize().padding(horizontal = DtPadding.medium)
    ) {
        // Action bar with controls
        DtSidecarActionBar()

        // Status section showing the current state
        DtSidecarStatusSection()

        // Main console with logs
        DtMainConsole(Modifier.fillMaxSize())
    }
}
