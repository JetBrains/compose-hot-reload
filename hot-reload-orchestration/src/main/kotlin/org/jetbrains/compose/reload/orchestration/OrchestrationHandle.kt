/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.orchestration

import org.jetbrains.compose.reload.core.Disposable
import org.jetbrains.compose.reload.core.Future
import org.jetbrains.compose.reload.core.getBlocking
import org.jetbrains.compose.reload.core.globalLaunch
import kotlin.time.Duration


public interface OrchestrationHandle : AutoCloseable {
    public val port: Int
    public fun invokeWhenClosed(action: () -> Unit): Disposable
    public fun invokeWhenMessageReceived(action: (OrchestrationMessage) -> Unit): Disposable


    public suspend fun sendMessage(message: OrchestrationMessage)

    /**
     * Will gracefully close the orchestration; The returned future shall not be awaited on the orchestration thread
     */
    public suspend fun closeGracefully()

    /**
     * Can be used as 'Shutdown Hook' to close the sockets immediately.
     * Note: This will not invoke any close listeners! Use the default '.close' instead.
     */
    public fun closeImmediately()
}

public fun OrchestrationHandle.sendMessageAsync(message: OrchestrationMessage): Future<Unit> = globalLaunch {
    sendMessage(message)
}

public fun OrchestrationHandle.sendMessageBlocking(message: OrchestrationMessage): Result<Unit> = globalLaunch {
    sendMessage(message)
}.getBlocking()

public fun OrchestrationHandle.sendMessageBlocking(
    message: OrchestrationMessage, timeout: Duration
): Result<Unit> = globalLaunch {
    sendMessage(message)
}.getBlocking(timeout)

public inline fun <reified T> OrchestrationHandle.invokeWhenReceived(crossinline action: (T) -> Unit): Disposable {
    return invokeWhenMessageReceived { message ->
        if (message is T) action(message)
    }
}
