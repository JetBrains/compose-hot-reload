/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.compose.devtools.sidecar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.devtools.theme.DtColors
import org.jetbrains.compose.devtools.theme.DtPadding
import org.jetbrains.compose.devtools.theme.DtShapes
import org.jetbrains.compose.devtools.widgets.DtHeader2
import org.jetbrains.compose.devtools.widgets.animatedReloadStatusBorder

@Composable
fun DtSidecarStatusSection() {
    Column(verticalArrangement = Arrangement.spacedBy(DtPadding.medium)) {
        // Modern header with proper spacing
        DtHeader2(
            "Status", 
            modifier = Modifier.padding(vertical = DtPadding.small)
        )

        // Status card with rounded corners and modern styling
        Surface(
            color = DtColors.surface,
            modifier = Modifier
                .fillMaxWidth()
                .clip(DtShapes.RoundedCornerShape)
                .animatedReloadStatusBorder(shape = DtShapes.RoundedCornerShape)
        ) {
            Column(
                modifier = Modifier.padding(DtPadding.medium),
                verticalArrangement = Arrangement.spacedBy(DtPadding.small)
            ) {
                DtReloadStatusItem()
                DtExpandedReloadCounterStatusItem()
                DtMissingJbrStatusItem()
                DtRuntimeErrorStatusItem()
            }
        }
    }
}


@Composable
fun DtSidecarStatusItem(
    symbol: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .padding(vertical = DtPadding.small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DtPadding.medium)
    ) {
        Box(
            modifier = Modifier.size(20.dp),
            contentAlignment = Alignment.Center
        ) { 
            symbol() 
        }

        // Content (usually text)
        content()
    }
}
