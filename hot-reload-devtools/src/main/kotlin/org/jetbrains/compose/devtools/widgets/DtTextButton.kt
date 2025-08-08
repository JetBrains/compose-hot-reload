/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.compose.devtools.widgets

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.devtools.Tag
import org.jetbrains.compose.devtools.theme.DtImages
import org.jetbrains.compose.devtools.theme.DtPadding
import org.jetbrains.compose.devtools.theme.DtSizes

/**
 * A button with text and an optional icon
 */
@Composable
fun DtTextButton(
    text: String,
    modifier: Modifier = Modifier,
    tag: Tag? = null,
    icon: DtImages.Image? = null,
    onClick: () -> Unit = {}
) {
    DtButton(
        onClick = onClick,
        modifier = modifier,
        tag = tag,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(DtPadding.buttonPadding)
        ) {
            // Show icon if provided
            if (icon != null) {
                DtImage(
                    image = icon,
                    modifier = Modifier.size(DtSizes.iconSize),
                    tint = Color.White
                )
                Spacer(Modifier.width(DtPadding.small))
            }

            // Text label
            DtText(text = text)
        }
    }
}
