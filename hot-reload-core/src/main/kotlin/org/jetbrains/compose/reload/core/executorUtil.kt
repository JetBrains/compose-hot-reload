/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.core

import org.jetbrains.compose.reload.InternalHotReloadApi
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future

/**
 * The regular [java.util.concurrent.ExecutorService.submit] function might throw an exception
 * when the submission was rejected (e.g., because the service was closed).
 * This function will catch issues when submitting the [block] and convert it to a 'failed future' instead.
 */
@InternalHotReloadApi
public fun <T> ExecutorService.submitSafe(block: () -> T): Future<T> {
    return try {
        submit(block)
    } catch (t: Throwable) {
        CompletableFuture.failedFuture<T>(t)
    }
}
