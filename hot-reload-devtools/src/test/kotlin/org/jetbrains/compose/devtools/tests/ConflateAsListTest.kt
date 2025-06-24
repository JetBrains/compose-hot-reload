/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.devtools.tests

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.devtools.conflateAsList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class ConflateAsListTest {
    @Test
    fun `test - congestion`() = runTest {
        val flow = flow {
            repeat(10) { emit(it) }
        }

        val elements = flow.conflateAsList().onEach { delay(1.seconds) }.toList()

        assertEquals(
            listOf(listOf(0), listOf(1, 2, 3, 4, 5, 6, 7, 8, 9)), elements
        )
    }

    @Test
    fun `test - no congestion`() = runTest {
        val flow = flow {
            repeat(3) {
                delay(1.seconds)
                emit(it)
            }
        }

        val elements = flow.conflateAsList().toList()

        assertEquals(
            listOf(listOf(0), listOf(1), listOf(2)), elements
        )
    }
}
