/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.compose.devtools

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

enum class Tag {
    ReloadCounterText,

    ReloadStatusSymbol,
    ReloadStatusText,

    RuntimeErrorSymbol,
    RuntimeErrorText,

    HotReloadLogo,
    BuildSystemLogo,

    ActionButton,
    ExpandMinimiseButton,

    Console,
}

internal fun Modifier.tag(tags: Tag?): Modifier = tags?.let { testTag(it.name) } ?: this
