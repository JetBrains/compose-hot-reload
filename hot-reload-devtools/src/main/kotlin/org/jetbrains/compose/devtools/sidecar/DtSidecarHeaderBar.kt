/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.compose.devtools.sidecar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.devtools.Tag
import org.jetbrains.compose.devtools.theme.DtPadding
import org.jetbrains.compose.devtools.theme.DtSizes
import org.jetbrains.compose.devtools.theme.DtTitles.COMPOSE_HOT_RELOAD_TITLE
import org.jetbrains.compose.devtools.widgets.DtCloseButton
import org.jetbrains.compose.devtools.widgets.DtComposeLogo
import org.jetbrains.compose.devtools.widgets.DtHeader1
import org.jetbrains.compose.devtools.widgets.animateReloadStatusColor

@Composable
internal fun DtAttachedSidecarHeaderBar(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(
            DtPadding.mediumElementPadding
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DtComposeLogo(
            modifier = Modifier.size(DtSizes.logoSize),
            tint = animateReloadStatusColor(
                idleColor = Color.White,
            ).value
        )
        DtHeader1(COMPOSE_HOT_RELOAD_TITLE)
        Spacer(Modifier.weight(1f))
        DtCloseButton(
            onClick = onClose,
            modifier = Modifier.size(DtSizes.logoSize),
            tag = Tag.ExpandMinimiseButton,
        )
    }
}


@Composable
internal fun DtDetachedSidecarHeaderBar(
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(
            DtPadding.mediumElementPadding
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DtComposeLogo(
            modifier = Modifier.size(DtSizes.logoSize),
            tint = animateReloadStatusColor(
                idleColor = Color.White,
            ).value
        )
        DtHeader1(COMPOSE_HOT_RELOAD_TITLE)
    }
}
