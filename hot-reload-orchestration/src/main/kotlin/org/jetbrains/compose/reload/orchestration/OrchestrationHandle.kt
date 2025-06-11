/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.orchestration

import org.jetbrains.compose.reload.core.Broadcast
import org.jetbrains.compose.reload.core.Future
import org.jetbrains.compose.reload.core.getBlocking
import org.jetbrains.compose.reload.core.getOrThrow
import org.jetbrains.compose.reload.core.launchTask
import org.jetbrains.compose.reload.core.reloadMainDispatcherImmediate
import org.jetbrains.compose.reload.core.reloadMainThread
import kotlin.time.Duration.Companion.seconds

public interface OrchestrationHandle : AutoCloseable {
    public val port: Future<Int>
    public val messages: Broadcast<OrchestrationMessage>
    public val closed: Future<Unit>

    public suspend infix fun send(message: OrchestrationMessage)
    public suspend fun isActive(): Boolean
    public suspend fun shutdown(): Boolean

    override fun close() {
        val shutdownTask = launchTask("$this.close()", reloadMainDispatcherImmediate) {
            shutdown()
        }

        if (Thread.currentThread() != reloadMainThread) {
            shutdownTask.getBlocking(15.seconds).getOrThrow()
        }
    }
}
