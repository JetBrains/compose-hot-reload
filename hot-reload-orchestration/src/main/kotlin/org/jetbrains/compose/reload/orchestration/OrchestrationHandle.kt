/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.orchestration

import org.jetbrains.compose.reload.core.Disposable
import java.util.concurrent.Future


public interface OrchestrationHandle : AutoCloseable {
    public val port: Int
    public fun invokeWhenClosed(action: () -> Unit)
    public fun invokeWhenMessageReceived(action: (OrchestrationMessage) -> Unit): Disposable
    public fun sendMessage(message: OrchestrationMessage): Future<Unit>

    /**
     * Will gracefully close the orchestration; The returned future shall not be awaited on the orchestration thread
     */
    public fun closeGracefully(): Future<Unit>

    /**
     * Can be used as 'Shutdown Hook' to close the sockets immediately.
     * Note: This will not invoke any close listeners! Use the default '.close' instead.
     */
    public fun closeImmediately()
}

public inline fun <reified T> OrchestrationHandle.invokeWhenReceived(crossinline action: (T) -> Unit): Disposable {
    return invokeWhenMessageReceived { message ->
        if (message is T) action(message)
    }
}
