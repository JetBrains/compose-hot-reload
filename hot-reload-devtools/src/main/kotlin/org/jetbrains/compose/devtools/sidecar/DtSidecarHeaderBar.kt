/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.compose.devtools.sidecar

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.devtools.theme.DtPadding
import org.jetbrains.compose.devtools.theme.DtTitles.COMPOSE_HOT_RELOAD_TOOLING
import org.jetbrains.compose.devtools.widgets.DtButton
import org.jetbrains.compose.devtools.widgets.DtComposeLogo
import org.jetbrains.compose.devtools.widgets.DtHeader1

@Composable
internal fun DtAttachedSidecarHeaderBar(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .padding(horizontal = DtPadding.large, vertical = DtPadding.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DtComposeLogo(modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(DtPadding.medium))
        DtHeader1(COMPOSE_HOT_RELOAD_TOOLING)
        Spacer(Modifier.weight(1f))
        DtButton(
            onClick = onClose,
            modifier = Modifier
                .padding(2.dp)
                .size(28.dp)
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Close",
                modifier = Modifier.fillMaxSize(),
                tint = Color.White
            )
        }
    }
}


@Composable
internal fun DtDetachedSidecarHeaderBar(
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .padding(horizontal = DtPadding.large, vertical = DtPadding.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DtComposeLogo(modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(DtPadding.medium))
        DtHeader1(COMPOSE_HOT_RELOAD_TOOLING)
    }
}
