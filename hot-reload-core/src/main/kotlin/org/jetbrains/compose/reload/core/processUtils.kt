/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.core

import org.jetbrains.compose.reload.InternalHotReloadApi
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.jvm.optionals.getOrNull
import kotlin.streams.asSequence

private val logger = createLogger()

@InternalHotReloadApi
public fun Process.destroyWithDescendants(): Boolean {
    return toHandle().destroyWithDescendants()
}

@InternalHotReloadApi
public fun ProcessHandle.destroyWithDescendants(): Boolean {
    val exits = withClosure { handle -> handle.children().asSequence().toList() }.reversed()
        .onEach { handle -> if (!handle.destroy()) handle.destroyForcibly() }
        .map { it.onExit() }

    try {
        CompletableFuture.allOf(*exits.toTypedArray()).get(5, TimeUnit.SECONDS)
        return true
    } catch (t: Throwable) {
        logger.error("Process '${pid()}' (${info().command().getOrNull()} was not destroyed within time", t)
        return false
    }
}
