/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.orchestration

import org.jetbrains.compose.reload.core.Broadcast
import org.jetbrains.compose.reload.core.Future
import org.jetbrains.compose.reload.core.Task
import org.jetbrains.compose.reload.core.Try
import org.jetbrains.compose.reload.core.flatten
import org.jetbrains.compose.reload.core.getBlocking
import org.jetbrains.compose.reload.core.invokeOnCompletion
import org.jetbrains.compose.reload.core.launchTask
import org.jetbrains.compose.reload.core.mapLeft
import kotlin.time.Duration.Companion.seconds

private val timeout = 15.seconds

public interface OrchestrationHandle : AutoCloseable, Task<Unit> {
    public val port: Future<Int>
    public val messages: Broadcast<OrchestrationMessage>

    public suspend infix fun send(message: OrchestrationMessage)

    override fun close() {
        stop()
    }
}

public infix fun OrchestrationHandle.sendBlocking(message: OrchestrationMessage): Try<Unit> {
    return launchTask("sendBlocking") {
        send(message)
    }.getBlocking(timeout)
}

public infix fun OrchestrationHandle.sendAsync(message: OrchestrationMessage): Future<Unit> {
    return launchTask("sendAsync") {
        send(message)
    }
}

public infix fun OrchestrationHandle.invokeOnClose(action: () -> Unit) {
    invokeOnCompletion { action() }
}

public fun OrchestrationClient.connectBlocking(): Try<OrchestrationClient> {
    return launchTask("connectBlocking") {
        connect()
    }.getBlocking(timeout).flatten().mapLeft { this }
}

public fun OrchestrationServer.startBlocking(): Try<Unit> {
    return launchTask("startBlocking") {
        start()
    }.getBlocking(timeout)
}
