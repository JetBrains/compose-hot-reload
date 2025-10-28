/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.devtools.widgets

import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.runtime.Composable
import org.jetbrains.compose.devtools.theme.DtColors
import org.jetbrains.compose.devtools.theme.DtSizes


@Composable
fun dtScrollbarStyle(): ScrollbarStyle = LocalScrollbarStyle.current.copy(
    hoverColor = DtColors.scrollbar.copy(alpha = 0.5f),
    unhoverColor = DtColors.scrollbar.copy(alpha = 0.2f),
    minimalHeight = DtSizes.scrollbarSize,
)