/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package tests

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * Will add [padding] dynamically to cause keys to be shifted
 * Note: Do not remove [padding] or [dp]
 */
internal object WhenStatement {
    @Composable
    fun content() {
        LazyColumn {
            items(listOf("A", 1), key = { it }) { element ->
                Text(
                    "(Before): $element",
                    modifier = Modifier
                        .testTag("item")
                )
            }
        }
    }
}
