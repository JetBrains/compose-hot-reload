/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package tests

import androidx.compose.material3.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

internal object I104NonDurableLambdaNames {
    @Composable
    fun render() {
        var note by remember { mutableStateOf("") }
        Surface {
            Text(note)
        }
    }
}

internal object I104DefaultLambdaNames {
    inline fun <T> inlineHazard(a: List<T>, b: (item: T) -> Any? = { null }, c: (item: T) -> Unit) = Unit

    @Composable
    fun render() {
        val array = mutableListOf("A", "B", "C")
        inlineHazard(array) { }
    }
}
