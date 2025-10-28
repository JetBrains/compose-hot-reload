/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.devtools.widgets

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.devtools.Tag
import org.jetbrains.compose.devtools.theme.DtColors

/**
 * A button with an icon
 */

@Composable
fun DtIconButton(
    onClick: () -> Unit = {},
    tooltip: String? = null,
    modifier: Modifier = Modifier,
    tag: Tag? = null,
    shape: Shape = RoundedCornerShape(6.dp),
    contentModifier: Modifier = Modifier,
    content: @Composable (state: DtButtonState) -> Unit = { _ -> },
) = DtButton(
    onClick = onClick,
    tooltip = tooltip,
    modifier = modifier,
    backgroundColor = { isHovered -> if (isHovered) DtColors.defaultActive else DtColors.applicationBackground },
    tag = tag,
    shape = shape,
    border = null,
    contentModifier = contentModifier,
    content = content
)
