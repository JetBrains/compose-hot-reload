/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.devtools.theme

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

object DtSizes {
    val iconSize = 16.dp

    val statusItemSize = 16.dp
    val scrollbarSize = 64.dp

    val reloadCounterSize = 20.dp

    val logoSize = 24.dp
    val largeLogoSize = 28.dp

    val minButtonSize = 24.dp

    val defaultWindowWidth = 512.dp
    val defaultWindowHeight = 512.dp
    val defaultWindowSize = DpSize(defaultWindowWidth, defaultWindowHeight)

    val sidecarWidth = defaultWindowWidth
    val minSidecarHeight = defaultWindowHeight

    val maxNotificationCardHeight = 128.dp
}
