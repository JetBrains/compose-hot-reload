/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.compose.devtools.sidecar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.devtools.theme.DtColors
import org.jetbrains.compose.devtools.theme.DtPadding
import org.jetbrains.compose.devtools.theme.dtHorizontalPadding
import org.jetbrains.compose.devtools.theme.dtVerticalPadding
import org.jetbrains.compose.devtools.widgets.DtHeader2
import org.jetbrains.compose.devtools.widgets.animatedReloadStatusBorder

@Composable
fun DtSidecarStatusSection() {
    Column(verticalArrangement = Arrangement.spacedBy(DtPadding.vertical)) {
        DtHeader2("Status", modifier = Modifier.Companion.dtHorizontalPadding())
        Box(
            modifier = Modifier.dtHorizontalPadding()
                .fillMaxWidth()
                .background(DtColors.surface)
                .animatedReloadStatusBorder()
        ) {
            Column(
                modifier = Modifier
                    .dtHorizontalPadding()
                    .dtVerticalPadding()
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
        modifier = Modifier.fillMaxWidth().height(24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DtPadding.arrangement)
    ) {

        Box(modifier = Modifier.size(18.dp)) { symbol() }
        content()
    }
}
