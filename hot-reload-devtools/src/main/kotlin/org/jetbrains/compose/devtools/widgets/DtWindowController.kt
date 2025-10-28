/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.devtools.widgets

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

interface DtWindowController {
    val isOpen: State<Boolean>
    val focusRequests: Flow<FocusRequest>

    object FocusRequest

    fun open()
    fun close()
    fun toggle()
    fun requestFocus()
}

@Composable
internal fun rememberWindowController(): DtWindowController {
    val isOpenState = remember { mutableStateOf(false) }
    val focusRequestsFlow = MutableSharedFlow<DtWindowController.FocusRequest>()
    val scope = rememberCoroutineScope()

    val controller = remember {
        object : DtWindowController {
            override val isOpen: State<Boolean> get() = isOpenState
            override val focusRequests: Flow<DtWindowController.FocusRequest> get() = focusRequestsFlow

            override fun open() { isOpenState.value = true }
            override fun close() { isOpenState.value = false }
            override fun toggle() { isOpenState.value = !isOpenState.value }
            override fun requestFocus() {
                if (!isOpenState.value) isOpenState.value = true
                scope.launch {
                    focusRequestsFlow.emit(DtWindowController.FocusRequest)
                }
            }
        }
    }
    return controller
}