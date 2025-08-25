/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

@file:Suppress("PreferCurrentCoroutineContextToCoroutineContext")

package org.jetbrains.compose.reload.core

import kotlinx.coroutines.Job
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
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

internal fun CoroutineContext.hasActiveJob(): Boolean {
    return if (isKotlinxCoroutinesAvailable) this[Job]?.isActive ?: false
    else false
}

suspend fun currentCoroutineContext(): CoroutineContext {
    return coroutineContext
}
