/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.core

import kotlinx.coroutines.Job
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.Continuation
import kotlin.coroutines.suspendCoroutine


internal val isKotlinxCoroutinesAvailable = runCatching {
    Job::class.java.classLoader != null
}.isSuccess

internal suspend fun <T> suspendCancellableCoroutine(action: (Continuation<T>) -> Unit): T {
    return if (isKotlinxCoroutinesAvailable) {
        suspendCancellableCoroutine { action(it) }
    } else {
        suspendCoroutine(action)
    }
}
