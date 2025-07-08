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
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.devtools.Tag
import org.jetbrains.compose.devtools.theme.DtPadding

/**
 * A button with text and an optional icon
 */
@Composable
fun DtTextButton(
    text: String,
    modifier: Modifier = Modifier,
    tag: Tag? = null,
    icon: ImageVector? = null,
    onClick: () -> Unit = {}
) {
    DtButton(
        onClick = onClick,
        modifier = modifier,
        tag = tag,
    ) { buttonState ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = DtPadding.medium, vertical = DtPadding.small)
        ) {
            // Show icon if provided
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = text,
                    modifier = Modifier.size(16.dp),
                    tint = Color.White
                )
                Spacer(Modifier.width(DtPadding.small))
            }

            // Text label
            DtText(text = text)
        }
    }
}
