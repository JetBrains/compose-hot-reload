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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.devtools.theme.DtPadding
import org.jetbrains.compose.devtools.theme.DtShapes
import org.jetbrains.compose.devtools.theme.DtSizes
import org.jetbrains.compose.devtools.widgets.DtHeader2
import org.jetbrains.compose.devtools.widgets.animatedReloadStatusBorder

@Composable
fun DtSidecarStatusSection() {
    Column(verticalArrangement = Arrangement.spacedBy(DtPadding.smallElementPadding)) {
        DtHeader2("Status")

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(DtShapes.RoundedCornerShape)
                .animatedReloadStatusBorder(idleColor = Color.Gray, resetErrorState = true)
        ) {
            Column(
                modifier = Modifier.padding(DtPadding.mediumElementPadding),
                verticalArrangement = Arrangement.spacedBy(DtPadding.smallElementPadding)
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
            .height(DtSizes.statusItemSize),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DtPadding.smallElementPadding)
    ) {
        Box(
            modifier = Modifier.size(DtSizes.statusItemSize),
            contentAlignment = Alignment.Center
        ) { 
            symbol() 
        }

        // Content (usually text)
        content()
    }
}
