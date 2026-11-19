/*
 * Copyright 2024-2026 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.shared

import org.jetbrains.compose.reload.core.Disposable
import org.jetbrains.compose.reload.core.Left
import org.jetbrains.compose.reload.core.Right
import org.jetbrains.compose.reload.core.Try
import org.jetbrains.compose.reload.core.toLeft
import org.jetbrains.compose.reload.orchestration.OrchestrationMessageId

/**
 * Describes a successful reload. Each runtime provides its own implementation
 * (e.g. the desktop agent's 'Reload', which carries the redefined classes and dirty Compose scopes).
 */
interface ReloadResult

private val beforeReloadListeners =
    mutableListOf<(reloadRequestId: OrchestrationMessageId) -> Unit>()

private val afterReloadListeners =
    mutableListOf<(reloadRequestId: OrchestrationMessageId, result: Try<ReloadResult>) -> Unit>()

fun invokeBeforeHotReload(block: (reloadRequestId: OrchestrationMessageId) -> Unit): Disposable =
    synchronized(beforeReloadListeners) {
        beforeReloadListeners.add(block)
        Disposable {
            synchronized(beforeReloadListeners) {
                beforeReloadListeners.remove(block)
            }
        }
    }

fun invokeAfterHotReload(block: (reloadRequestId: OrchestrationMessageId, result: Try<ReloadResult>) -> Unit): Disposable =
    synchronized(afterReloadListeners) {
        afterReloadListeners.add(block)
        Disposable {
            synchronized(afterReloadListeners) {
                afterReloadListeners.remove(block)
            }
        }
    }

/**
 * Typed view on [invokeAfterHotReload]: [block] receives the reload result typed with [R]
 * Failures pass through unchanged; a success payload that
 * is not [R] (checked at runtime) skips the block.
 */
inline fun <reified R : ReloadResult> invokeAfterHotReloadOf(
    crossinline block: (reloadRequestId: OrchestrationMessageId, result: Try<R>) -> Unit,
): Disposable = invokeAfterHotReload { reloadRequestId, result ->
    block(reloadRequestId, result.asReloadResultOf<R>() ?: return@invokeAfterHotReload)
}

@Suppress("UNCHECKED_CAST")
@PublishedApi
internal fun <R : ReloadResult> Try<ReloadResult>.asReloadResultOf(): Try<R>? = when (this) {
    is Left -> (value as? R)?.toLeft()
    is Right -> this
}

fun executeBeforeHotReloadListeners(reloadRequestId: OrchestrationMessageId) {
    val listeners = synchronized(beforeReloadListeners) { beforeReloadListeners.toList() }
    listeners.forEach { listener -> listener.invoke(reloadRequestId) }
}

fun executeAfterHotReloadListeners(reloadRequestId: OrchestrationMessageId, result: Try<ReloadResult>) {
    val listeners = synchronized(afterReloadListeners) { afterReloadListeners.toList() }
    listeners.forEach { listener -> listener.invoke(reloadRequestId, result) }
}
