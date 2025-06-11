/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.orchestration

import org.jetbrains.compose.reload.core.Disposable
import org.jetbrains.compose.reload.core.Try
import org.jetbrains.compose.reload.core.getBlocking
import org.jetbrains.compose.reload.core.getOrThrow
import org.jetbrains.compose.reload.core.invokeOnCompletion
import org.jetbrains.compose.reload.core.isActive
import org.jetbrains.compose.reload.core.launchTask
import org.jetbrains.compose.reload.core.mapLeft
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

public interface OrchestrationClientBlocking : AutoCloseable {
    public fun connect(): Try<OrchestrationClientBlocking>
    public fun port(): Int
    public fun isActive(): Boolean
    public fun invokeOnClose(action: () -> Unit): Disposable
    public infix fun send(message: OrchestrationMessage)
}

public fun OrchestrationClient.asBlocking(timeout: Duration = 15.seconds): OrchestrationClientBlocking =
    OrchestrationClientBlockingImpl(this, timeout)

private class OrchestrationClientBlockingImpl(
    private val handle: OrchestrationClient,
    private val timeout: Duration = 15.seconds
) : OrchestrationClientBlocking {

    override fun connect(): Try<OrchestrationClientBlocking> {
        return launchTask("blocking.connect()") { handle.connect() }
            .getBlocking(timeout).getOrThrow()
            .mapLeft { this }
    }

    override fun port(): Int = handle.port.getBlocking(timeout).getOrThrow()

    override fun isActive(): Boolean {
        return launchTask("blocking.isActive()") { handle.isActive() }.getBlocking(timeout).getOrThrow()
    }

    override fun invokeOnClose(action: () -> Unit): Disposable {
        return handle.invokeOnCompletion { action() }
    }

    override fun send(message: OrchestrationMessage) {
        launchTask("blocking.send") { handle.send(message) }.getBlocking(timeout).getOrThrow()
    }

    override fun close() {
        handle.stop()
    }
}
