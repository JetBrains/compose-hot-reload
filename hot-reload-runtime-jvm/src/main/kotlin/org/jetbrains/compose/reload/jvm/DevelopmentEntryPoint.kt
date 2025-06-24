/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

@file:JvmName("JvmDevelopmentEntryPoint")

package org.jetbrains.compose.reload.jvm

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.awt.ComposeDialog
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.window.DialogWindowScope
import androidx.compose.ui.window.FrameWindowScope

@Composable
public fun DevelopmentEntryPoint(child: @Composable () -> Unit) {
    child()
}


@Suppress("unused", "EXTENSION_SHADOWED_BY_MEMBER") // used by instrumentation
@OptIn(ExperimentalComposeUiApi::class)
@PublishedApi
internal fun ComposeWindow.setContent(
    onPreviewKeyEvent: (KeyEvent) -> Boolean,
    onKeyEvent: (KeyEvent) -> Boolean,
    content: @Composable FrameWindowScope.() -> Unit
) {
    setContent(onPreviewKeyEvent = onPreviewKeyEvent, onKeyEvent = onKeyEvent) {
        DevelopmentEntryPoint {
            content()
        }
    }
}

@Suppress("unused", "EXTENSION_SHADOWED_BY_MEMBER") // used by instrumentation
@OptIn(ExperimentalComposeUiApi::class)
@PublishedApi
internal fun ComposeWindow.setContent(
    content: @Composable FrameWindowScope.() -> Unit
) {
    setContent {
        DevelopmentEntryPoint {
            content()
        }
    }
}

@Suppress("unused", "EXTENSION_SHADOWED_BY_MEMBER") // used by instrumentation
@OptIn(ExperimentalComposeUiApi::class)
@PublishedApi
internal fun ComposeDialog.setContent(
    onPreviewKeyEvent: (KeyEvent) -> Boolean,
    onKeyEvent: (KeyEvent) -> Boolean,
    content: @Composable DialogWindowScope.() -> Unit
) {
    setContent(onPreviewKeyEvent = onPreviewKeyEvent, onKeyEvent = onKeyEvent) {
        DevelopmentEntryPoint {
            content()
        }
    }
}

@Suppress("unused", "EXTENSION_SHADOWED_BY_MEMBER") // used by instrumentation
@OptIn(ExperimentalComposeUiApi::class)
@PublishedApi
internal fun ComposeDialog.setContent(
    content: @Composable DialogWindowScope.() -> Unit
) {
    setContent {
        DevelopmentEntryPoint {
            content()
        }
    }
}

