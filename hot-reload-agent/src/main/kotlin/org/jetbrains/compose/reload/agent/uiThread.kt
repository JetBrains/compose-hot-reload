/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.compose.reload.agent

import org.jetbrains.compose.reload.core.Future
import org.jetbrains.compose.reload.core.complete
import org.jetbrains.compose.reload.core.completeExceptionally
import org.jetbrains.compose.reload.core.getOrThrow
import javax.swing.SwingUtilities

internal val isUiThread: Boolean get() = SwingUtilities.isEventDispatchThread()

internal fun <T> runOnUiThread(action: () -> T): Future<T> {
    val future = Future<T>()
    SwingUtilities.invokeLater {
        try {
            future.complete(action())
        } catch (t: Throwable) {
            future.completeExceptionally(t)
        }
    }
    return future
}


internal suspend fun <T> uiThread(action: () -> T): T {
    return runOnUiThread(action).await().getOrThrow()
}
