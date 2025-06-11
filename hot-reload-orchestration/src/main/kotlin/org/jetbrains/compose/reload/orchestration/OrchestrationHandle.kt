/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.orchestration

import org.jetbrains.compose.reload.core.Broadcast
import org.jetbrains.compose.reload.core.Future
import org.jetbrains.compose.reload.core.Task

public interface OrchestrationHandle : AutoCloseable, Task<Nothing> {
    public val port: Future<Int>
    public val messages: Broadcast<OrchestrationMessage>

    public suspend infix fun send(message: OrchestrationMessage)

    override fun close() {
        stop()
    }
}
