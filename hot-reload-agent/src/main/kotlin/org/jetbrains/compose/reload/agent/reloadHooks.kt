/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.agent

import org.jetbrains.compose.reload.core.Disposable
import org.jetbrains.compose.reload.core.Try
import org.jetbrains.compose.reload.orchestration.OrchestrationMessageId

private val beforeReloadListeners =
    mutableListOf<(reloadRequestId: OrchestrationMessageId) -> Unit>()

private val afterReloadListeners =
    mutableListOf<(reloadRequestId: OrchestrationMessageId, result: Try<Reload>) -> Unit>()

fun invokeBeforeHotReload(block: (reloadRequestId: OrchestrationMessageId) -> Unit): Disposable =
    synchronized(beforeReloadListeners) {
        beforeReloadListeners.add(block)
        Disposable {
            synchronized(beforeReloadListeners) {
                beforeReloadListeners.remove(block)
            }
        }
    }

fun invokeAfterHotReload(block: (reloadRequestId: OrchestrationMessageId, result: Try<Reload>) -> Unit): Disposable =
    synchronized(afterReloadListeners) {
        afterReloadListeners.add(block)
        Disposable {
            synchronized(afterReloadListeners) {
                afterReloadListeners.remove(block)
            }
        }
    }

internal fun executeBeforeHotReloadListeners(reloadRequestId: OrchestrationMessageId) {
    val listeners = synchronized(beforeReloadListeners) { beforeReloadListeners.toList() }
    listeners.forEach { listener -> listener.invoke(reloadRequestId) }
}

internal fun executeAfterHotReloadListeners(reloadRequestId: OrchestrationMessageId, result: Try<Reload>) {
    val listeners = synchronized(afterReloadListeners) { afterReloadListeners.toList() }
    listeners.forEach { listener -> listener.invoke(reloadRequestId, result) }
}
