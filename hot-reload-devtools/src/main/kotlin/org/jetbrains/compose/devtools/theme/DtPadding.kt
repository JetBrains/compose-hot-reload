/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.compose.devtools.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp

object DtPadding {
    // More consistent padding values for a modern UI
    val small = 4.dp
    val medium = 8.dp
    val large = 16.dp

    // Padding values between elements in containers
    val smallElementPadding = 8.dp
    val mediumElementPadding = 12.dp
    val largeElementPadding = 24.dp

    // Consistent border padding used across all windows
    val borderPadding = 20.dp

    // Common padding values for components
    val buttonPadding = PaddingValues(horizontal = large, vertical = medium)
}
