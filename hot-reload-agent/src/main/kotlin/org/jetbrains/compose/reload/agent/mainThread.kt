/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.compose.reload.agent

import java.util.concurrent.CompletableFuture
import javax.swing.SwingUtilities

internal val isMainThread: Boolean get() = SwingUtilities.isEventDispatchThread()

internal fun <T> runOnMainThread(action: () -> T): CompletableFuture<T> {
    val future = CompletableFuture<T>()
    SwingUtilities.invokeLater {
        try {
            future.complete(action())
        } catch (t: Throwable) {
            future.completeExceptionally(t)
        }
    }
    return future
}
